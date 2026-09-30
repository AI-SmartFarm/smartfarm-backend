package com.smartfarm.backend.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** 병의 원인. vector(매개충)는 바이러스 병(고추마일드모틀, 오이모자이크, TYLCV)에만 있다. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Cause(String type, String pathogen, String vector) {
}
