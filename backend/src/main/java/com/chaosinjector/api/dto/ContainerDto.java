package com.chaosinjector.api.dto;

import com.chaosinjector.target.model.TargetWorkload;

/** A container shown in the UI's target picker. */
public record ContainerDto(String id, String name, String image, String status) {

    public static ContainerDto from(TargetWorkload w) {
        return new ContainerDto(w.id(), w.name(), w.image(), w.status());
    }
}
