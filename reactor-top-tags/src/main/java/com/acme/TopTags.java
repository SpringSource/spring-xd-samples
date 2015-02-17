/*
 * Copyright 2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.acme;

import com.jayway.jsonpath.JsonPath;
import net.minidev.json.JSONArray;

import org.springframework.util.StringUtils;
import org.springframework.xd.reactor.Processor;
import org.springframework.xd.tuple.Tuple;

import reactor.fn.Predicate;
import reactor.rx.BiStreams;
import reactor.rx.Stream;
import reactor.rx.Streams;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.springframework.xd.tuple.TupleBuilder.tuple;

import java.util.LinkedHashMap;

/**
 * @author Mark Pollack
 * @author Marius Bogoevici
 */
public class TopTags implements Processor<String, Tuple> {

	private long timeWindow;

	private long timeShift;

	private int topN;


	public TopTags(long timeWindow, long timeShift, int topN) {
		this.timeWindow = timeWindow;
		this.timeShift = timeShift;
		this.topN = topN;
	}

	@Override
	public Stream<Tuple> process(Stream<String> stream) {


		return stream.flatMap(tweet -> {
			JSONArray array = JsonPath.read(tweet, "$.entities.hashtags[*].text");
			return Streams.from(array.toArray(new String[array.size()]));
		})

				.map(w -> reactor.fn.tuple.Tuple.of(w, 1))
				.window(timeWindow, timeShift, SECONDS)
				.map(s -> BiStreams.reduceByKey(s, (acc, next) -> acc + next)
						.sort((a, b) -> -a.t2.compareTo(b.t2))
						.take(topN))
				.flatMap(s -> s.reduce(new LinkedHashMap<>(),
						(m, v) -> {
							m.put(v.t1, v.t2);
							return m;
						}
				))
				.map(m -> tuple().of("topTags", m));
	}
}
