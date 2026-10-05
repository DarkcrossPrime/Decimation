package com.decimation.module.gun.data;


public record WeaponAssets(String model, String texture, String itemTexture,
                           String fireAnimation, String reloadAnimation) {
    public WeaponAssets {
        for (String value : new String[] {model, texture, itemTexture, fireAnimation, reloadAnimation}) {
            DefinitionValidation.identifier(value, "weapon asset");
        }
    }
}
