/*
 * Copyright 2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.api.internal.changedetection.state

import spock.lang.Shared

import static org.gradle.api.internal.changedetection.rules.ChangeType.*

class OrderInsensitiveTaskFilePropertyCompareStrategyTest extends AbstractTaskFilePropertyCompareStrategyTest {
    @Shared static TaskFilePropertyCompareStrategy includeAdded = new OrderInsensitiveTaskFilePropertyCompareStrategy(true)
    @Shared static TaskFilePropertyCompareStrategy excludeAdded = new OrderInsensitiveTaskFilePropertyCompareStrategy(false)

    def "detects addition"() {
        expect:
        changes(includeAdded, [one: snapshot(), two: snapshot()], [one: snapshot()]) == [change("two", ADDED)]
        changes(excludeAdded, [one: snapshot(), two: snapshot()], [one: snapshot()]) == []
    }

    def "detects deletion"() {
        expect:
        changes(strategy, [one: snapshot()], [one: snapshot(), two: snapshot()]) == [change("two", REMOVED)]
        where:
        strategy << [includeAdded, excludeAdded]
    }

    def "detects modification"() {
        expect:
        changes(strategy, [one: snapshot(), two: snapshot(false)], [one: snapshot(), two: snapshot()]) == [change("two", MODIFIED)]
        where:
        strategy << [includeAdded, excludeAdded]
    }

    def "detects replacement as addition and removal"() {
        expect:
        changes(includeAdded, [one: snapshot(), two: snapshot(), four: snapshot()], [one: snapshot(), three: snapshot(), four: snapshot()]) == [change("two", ADDED), change("three", REMOVED)]
        changes(excludeAdded, [one: snapshot(), two: snapshot(), four: snapshot()], [one: snapshot(), three: snapshot(), four: snapshot()]) == [change("three", REMOVED)]
    }

    def "does not detect reordering"() {
        expect:
        changes(strategy, [one: snapshot(), two: snapshot(), three: snapshot()], [one: snapshot(), three: snapshot(), two: snapshot()]) == []
        where:
        strategy << [includeAdded, excludeAdded]
    }
}
