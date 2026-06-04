package dev.brauw.mapper.tag;

import java.util.Set;

/**
 * A tag that matches a single exact value.
 * <p>
 * The stored value equals the tag's {@link #name() name}, so {@code spawn}
 * matches only {@code spawn}.
 */
public class SimpleTag extends Tag {

    /**
     * Creates a simple tag offered on the given regions.
     *
     * @param name             the exact value of the tag
     * @param usage            a short usage hint shown in commands
     * @param description      a human-readable description
     * @param supportedRegions the region names this tag is offered on
     */
    public SimpleTag(String name, String usage, String description, Set<String> supportedRegions) {
        super(name, usage, description, supportedRegions, false);
    }

    /**
     * Convenience constructor accepting the supported regions as varargs.
     */
    public SimpleTag(String name, String usage, String description, String... supportedRegions) {
        this(name, usage, description, Set.of(supportedRegions));
    }

    @Override
    public boolean matches(String value) {
        return name().equals(value);
    }
}
