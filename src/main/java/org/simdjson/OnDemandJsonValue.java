package org.simdjson;

import java.util.HashMap;
import java.util.Map;

/**
 * A node of the path-tree used by {@link PathsBasedJsonParser}. Each node represents a path segment
 * (object field name or array index) and may either be:
 * <ul>
 *     <li>An <em>internal</em> node, used only for routing the parser deeper, or</li>
 *     <li>A <em>leaf</em> node, where the parser stores the captured value for one of the user supplied paths.</li>
 * </ul>
 * <p>
 * The {@code version} field is bumped per leaf write so that a single parser instance can be reused across
 * many {@code parse()} calls without resetting per-node state. The {@code visitedVersion} field is used to
 * detect duplicate JSON keys within a single object so they are counted only once during structural walking.
 * <p>
 * This class is <strong>not</strong> thread-safe; it is intended to be owned by a single {@link PathsBasedJsonParser}.
 */
public class OnDemandJsonValue {

    private final Map<Object, OnDemandJsonValue> children;
    private OnDemandJsonValue parent;
    private ResolvedClass.ResolvedClassCategory type;
    private Object value;
    private long version;
    private long visitedVersion;
    private boolean isLeaf;

    public OnDemandJsonValue() {
        this.children = new HashMap<>();
        this.parent = null;
        this.value = null;
        this.version = 0L;
        this.visitedVersion = 0L;
        this.isLeaf = false;
    }

    public Map<Object, OnDemandJsonValue> getChildren() {
        return children;
    }

    public OnDemandJsonValue getParent() {
        return parent;
    }

    public void setParent(OnDemandJsonValue parent) {
        this.parent = parent;
    }

    public Object getValue() {
        return value;
    }

    public void setValue(Object value) {
        this.value = value;
    }

    public long getVersion() {
        return version;
    }

    public void setVersion(long version) {
        this.version = version;
    }

    public long getVisitedVersion() {
        return visitedVersion;
    }

    public void setVisitedVersion(long visitedVersion) {
        this.visitedVersion = visitedVersion;
    }

    public ResolvedClass.ResolvedClassCategory getType() {
        return type;
    }

    public void setType(ResolvedClass.ResolvedClassCategory type) {
        this.type = type;
    }

    public boolean isLeaf() {
        return isLeaf;
    }

    public void setLeaf(boolean leaf) {
        isLeaf = leaf;
    }
}
