/*
 * Copyright (c) 2013, Francis Galiegue <fgaliegue@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the Lesser GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * Lesser GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.github.fge.jsonschema.processing;

import com.github.fge.jsonschema.exceptions.ProcessingException;
import com.github.fge.jsonschema.exceptions.unchecked.ProcessorBuildError;
import com.github.fge.jsonschema.report.MessageProvider;
import com.github.fge.jsonschema.report.ProcessingMessage;
import com.github.fge.jsonschema.report.ProcessingReport;
import com.google.common.base.Function;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import net.jcip.annotations.NotThreadSafe;

import java.util.Map;

import static com.github.fge.jsonschema.messages.ProcessingErrors.*;

/**
 * {@link Map}-based processor selector, with an optional default processor
 *
 * <p>The processor produced by this class works as follows:</p>
 *
 * <ul>
 *     <li>a key, of type {@code K}, is computed from the processor input, of
 *     type {@code IN}, using a {@link Function};</li>
 *     <li>the processor then looks up this key in a {@link Map}, whose values
 *     are {@link Processor}s;</li>
 *     <li>if the key exists, the appropriate procesor is executed; otherwise,
 *     the default action is performed.</li>
 * </ul>
 *
 * <p>The default action depends on whether a default processor has been
 * supplied: if none exists, a {@link ProcessingException} is thrown.</p>
 *
 * <p>This class is meant to be extended, and the only method to implement is
 * {@link #f()}, which provides the function to compute keys.</p>
 *
 * <p>Note that while this class is not thread safe, the resulting processor
 * is <b>immutable</b>.</p>
 *
 * <p>Also note that <b>null keys are not allowed</b>.</p>
 *
 * @param <K> the type of keys in the map
 * @param <IN> the input type of processors
 * @param <OUT> the output type of processors
 */
@NotThreadSafe
public abstract class ProcessorMap<K, IN extends MessageProvider, OUT extends MessageProvider>
{
    /**
     * The map of processors
     */
    private final Map<K, Processor<IN, OUT>> processors = Maps.newHashMap();

    /**
     * The default processor
     */
    private Processor<IN, OUT> defaultProcessor = null;

    /**
     * Add an entry to the processor map
     *
     * @param key the key to match against
     * @param processor the processor for that key
     * @return this
     * @throws ProcessorBuildError either the key or the processor are null
     */
    public final ProcessorMap<K, IN, OUT> addEntry(final K key,
        final Processor<IN, OUT> processor)
    {
        if (key == null)
            throw new ProcessorBuildError(new ProcessingMessage()
                .message(NULL_KEY));
        if (processor == null)
            throw new ProcessorBuildError(new ProcessingMessage()
                .message(NULL_PROCESSOR));
        processors.put(key, processor);
        return this;
    }

    /**
     * Set the default processor if no matching key is found
     *
     * @param defaultProcessor the default processor
     * @return this
     * @throws ProcessorBuildError processor is null
     */
    public final ProcessorMap<K, IN, OUT> setDefaultProcessor(
        final Processor<IN, OUT> defaultProcessor)
    {
        if (defaultProcessor == null)
            throw new ProcessorBuildError(new ProcessingMessage()
                .message(NULL_PROCESSOR));
        this.defaultProcessor = defaultProcessor;
        return this;
    }

    /**
     * Build the resulting processor from this map selector
     *
     * <p>The resulting processor is immutable: reusing a map builder after
     * getting the processor by calling this method will not alter the
     * processor you grabbed.</p>
     *
     * @return the processor for this map selector
     * @throws ProcessorBuildError the function (provided by {@link #f()}) is
     * null
     */
    public final Processor<IN, OUT> getProcessor()
    {
        return new Mapper<K, IN, OUT>(processors, f(), defaultProcessor);
    }

    /**
     * Provide the function to compute a key out of an input
     *
     * @return the function
     */
    protected abstract Function<IN, K> f();

    private static final class Mapper<K, IN extends MessageProvider, OUT extends MessageProvider>
        implements Processor<IN, OUT>
    {
        private final Map<K, Processor<IN, OUT>> processors;
        private final Function<IN, K> f;
        private final Processor<IN, OUT> defaultProcessor;

        Mapper(final Map<K, Processor<IN, OUT>> processors,
            final Function<IN, K> f, final Processor<IN, OUT> defaultProcessor)
        {
            if (f == null)
                throw new ProcessorBuildError(new ProcessingMessage()
                    .message(NULL_FUNCTION));
            this.processors = ImmutableMap.copyOf(processors);
            this.f = f;
            this.defaultProcessor = defaultProcessor;
        }

        @Override
        public OUT process(final ProcessingReport report, final IN input)
            throws ProcessingException
        {
            final K key = f.apply(input);
            Processor<IN, OUT> processor = processors.get(key);

            if (processor == null)
                processor = defaultProcessor;

            if (processor == null) // Not even a default processor. Ouch.
                throw new ProcessingException(new ProcessingMessage()
                    .message(NO_SUITABLE_PROCESSOR).put("key", key));

            return processor.process(report, input);
        }
    }
}
