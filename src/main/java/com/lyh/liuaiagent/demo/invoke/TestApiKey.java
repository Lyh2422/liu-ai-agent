package com.lyh.liuaiagent.demo.invoke;

public interface TestApiKey {

    String API_KEY = System.getenv().getOrDefault("DASHSCOPE_API_KEY", "");
}
