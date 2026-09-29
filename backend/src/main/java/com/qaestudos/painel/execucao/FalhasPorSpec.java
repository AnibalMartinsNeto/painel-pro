package com.qaestudos.painel.execucao;

/** Projeção: quantas falhas um spec teve no período. */
public record FalhasPorSpec(String spec, long falhas) {}
