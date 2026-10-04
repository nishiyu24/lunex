package com.nishiyu.lunex.machine;

// 先頭付近にインターフェースを追加
public interface IResourceProvider {
    long getAmount();
    long getCapacity();
}