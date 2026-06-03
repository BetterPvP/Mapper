package dev.brauw.mapper.tag;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Represents a tag that can be applied to regions.
 * <p>
 * A tag is a <em>matcher</em> across two independent axes:
 * <ul>
 *     <li><b>Region support</b> &mdash; which region names this tag is offered
 *     on. A single tag may support many regions, so they do not have to be
 *     registered one by one (see {@link #supportsRegion(String)}).</li>
 *     <li><b>Value matching</b> &mdash; which concrete tag values this
 *     definition owns. A single definition may match several versions of the
 *     same tag, e.g. {@code level:37} and {@code level:30} (see
 *     {@link #matches(String)}).</li>
 * </ul>
 * This class is abstract and meant to be extended. {@link SimpleTag} matches a
 * single exact value; {@link PatternTag} matches values against a regular
 * expression. Further strategies can be added by subclassing.
 */
public abstract class Tag {

    private final String name;
    private final String usage;
    private final String description;
    private final Set<String> supportedRegions;

    /**
     * Creates a new tag.
     *
     * @param name             the identity of the tag, shown in GUIs and used as
     *                         the stored value for exact tags
     * @param usage            a short usage hint shown in commands
     * @param description      a human-readable description shown in commands and GUIs
     * @param supportedRegions the region names this tag is offered on
     */
    protected Tag(String name, String usage, String description, Set<String> supportedRegions) {
        this.name = name;
        this.usage = usage;
        this.description = description;
        this.supportedRegions = new HashSet<>(supportedRegions);
    }

    /**
     * Checks whether a concrete tag value belongs to this definition.
     * <p>
     * For example a {@code level} pattern tag would match both {@code level:37}
     * and {@code level:30}.
     *
     * @param value the concrete tag value to test
     * @return true if the value belongs to this tag, false otherwise
     */
    public abstract boolean matches(String value);

    /**
     * Checks whether this tag is offered on the given region.
     * <p>
     * Defaults to an exact match against the configured set of supported
     * regions. Subclasses may override to support pattern-based region matching.
     *
     * @param regionName the name of the region to test
     * @return true if this tag supports the region, false otherwise
     */
    public boolean supportsRegion(String regionName) {
        return supportedRegions.contains(regionName);
    }

    /**
     * @return an unmodifiable view of the region names this tag is offered on
     */
    public Set<String> supportedRegions() {
        return Collections.unmodifiableSet(supportedRegions);
    }

    /**
     * @return the identity of the tag, shown in GUIs and used as the stored value for exact tags
     */
    public String name() {
        return name;
    }

    /**
     * @return a short usage hint shown in commands
     */
    public String usage() {
        return usage;
    }

    /**
     * @return a human-readable description shown in commands and GUIs
     */
    public String description() {
        return description;
    }
}
