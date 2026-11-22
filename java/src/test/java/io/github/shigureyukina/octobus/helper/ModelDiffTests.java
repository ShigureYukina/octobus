package io.github.shigureyukina.octobus.helper;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ModelDiffTests {

    @Test
    void noChanges() {
        var oldModels = List.of("gpt-4", "gpt-4-turbo");
        var newModels = List.of("gpt-4", "gpt-4-turbo");
        var result = ModelDiff.diff(oldModels, newModels);
        assertThat(result.deleted()).isEmpty();
        assertThat(result.added()).isEmpty();
    }

    @Test
    void addedModels() {
        var oldModels = List.of("gpt-4");
        var newModels = List.of("gpt-4", "gpt-4-turbo");
        var result = ModelDiff.diff(oldModels, newModels);
        assertThat(result.deleted()).isEmpty();
        assertThat(result.added()).containsExactly("gpt-4-turbo");
    }

    @Test
    void deletedModels() {
        var oldModels = List.of("gpt-4", "gpt-4-turbo");
        var newModels = List.of("gpt-4");
        var result = ModelDiff.diff(oldModels, newModels);
        assertThat(result.added()).isEmpty();
        assertThat(result.deleted()).containsExactly("gpt-4-turbo");
    }

    @Test
    void addedAndDeleted() {
        var oldModels = List.of("gpt-4", "gpt-3.5-turbo");
        var newModels = List.of("gpt-4", "gpt-4-turbo");
        var result = ModelDiff.diff(oldModels, newModels);
        assertThat(result.deleted()).containsExactly("gpt-3.5-turbo");
        assertThat(result.added()).containsExactly("gpt-4-turbo");
    }

    @Test
    void caseInsensitiveMatching() {
        var oldModels = List.of("GPT-4", "GPT-4-Turbo");
        var newModels = List.of("gpt-4", "gpt-4-turbo");
        var result = ModelDiff.diff(oldModels, newModels);
        assertThat(result.deleted()).isEmpty();
        assertThat(result.added()).isEmpty();
    }

    @Test
    void preservesOriginalCaseInDeleted() {
        var oldModels = List.of("GPT-4", "gpt-3.5-turbo");
        var newModels = List.of("gpt-4");
        var result = ModelDiff.diff(oldModels, newModels);
        assertThat(result.deleted()).containsExactly("gpt-3.5-turbo");
        assertThat(result.added()).isEmpty();
    }

    @Test
    void preservesOriginalCaseInAdded() {
        var oldModels = List.of("gpt-4");
        var newModels = List.of("gpt-4", "GPT-4-TURBO");
        var result = ModelDiff.diff(oldModels, newModels);
        assertThat(result.deleted()).isEmpty();
        assertThat(result.added()).containsExactly("GPT-4-TURBO");
    }

    @Test
    void duplicateHandling() {
        var oldModels = List.of("gpt-4", "gpt-4");
        var newModels = List.of("gpt-4");
        var result = ModelDiff.diff(oldModels, newModels);
        assertThat(result.deleted()).containsExactly("gpt-4");
        assertThat(result.added()).isEmpty();
    }

    @Test
    void allNew() {
        var oldModels = List.<String>of();
        var newModels = List.of("gpt-4", "gpt-4-turbo");
        var result = ModelDiff.diff(oldModels, newModels);
        assertThat(result.deleted()).isEmpty();
        assertThat(result.added()).containsExactly("gpt-4", "gpt-4-turbo");
    }

    @Test
    void allRemoved() {
        var oldModels = List.of("gpt-4", "gpt-4-turbo");
        var newModels = List.<String>of();
        var result = ModelDiff.diff(oldModels, newModels);
        assertThat(result.deleted()).containsExactlyInAnyOrder("gpt-4", "gpt-4-turbo");
        assertThat(result.added()).isEmpty();
    }

    @Test
    void handlesNullEntries() {
        var oldModels = new java.util.ArrayList<String>();
        oldModels.add("gpt-4");
        oldModels.add(null);
        var newModels = List.of("gpt-4");
        var result = ModelDiff.diff(oldModels, newModels);
        assertThat(result.deleted()).isEmpty();
        assertThat(result.added()).isEmpty();
    }
}
