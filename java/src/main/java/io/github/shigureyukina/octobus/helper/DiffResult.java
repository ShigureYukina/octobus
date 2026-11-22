package io.github.shigureyukina.octobus.helper;

import java.util.List;

/**
 * Result of a model diff operation.
 *
 * @param deleted models present in old but absent in new (original case from old)
 * @param added   models present in new but absent in old (original case from new)
 */
public record DiffResult(List<String> deleted, List<String> added) {
}
