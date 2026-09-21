package com.decimation.client.content;

import com.google.gson.JsonObject;

public record DanimAnimation(int version, int length, boolean isStatic, int hand, JsonObject data) { }

