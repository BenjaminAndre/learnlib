/* Copyright (C) 2013-2019 TU Dortmund
 * This file is part of LearnLib, http://www.learnlib.de/.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.learnlib.examples;

import java.util.Collection;

import de.learnlib.api.query.DefaultQuery;
import net.automatalib.words.Alphabet;
import net.automatalib.words.Word;

public interface PassiveLearningExample<I, D> {

    Collection<DefaultQuery<I, D>> getSamples();

    Alphabet<I> getAlphabet();

    interface DFAPassiveLearningExample<I> extends PassiveLearningExample<I, Boolean> {}

    interface MealyPassiveLearningExample<I, O> extends PassiveLearningExample<I, Word<O>> {}
}