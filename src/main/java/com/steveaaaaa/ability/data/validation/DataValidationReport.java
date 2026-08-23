package com.steveaaaaa.ability.data.validation;

import java.util.Comparator;
import java.util.List;
import net.minecraft.resources.ResourceLocation;

public record DataValidationReport(List<Diagnostic> diagnostics) {
    public DataValidationReport {
        diagnostics = diagnostics.stream()
                .sorted(Comparator.comparing(Diagnostic::definitionType)
                        .thenComparing(Diagnostic::definitionId)
                        .thenComparing(Diagnostic::path)
                        .thenComparing(Diagnostic::message))
                .toList();
    }

    public boolean valid() {
        return diagnostics.isEmpty();
    }

    public int errorCount() {
        return diagnostics.size();
    }

    public record Diagnostic(
            String definitionType,
            ResourceLocation definitionId,
            String path,
            String message
    ) {
        public String displayText() {
            return definitionType + " " + definitionId + " [" + path + "]: " + message;
        }
    }
}
