package com.example.ai.aistudy.function;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import org.springframework.context.annotation.Description;

import java.util.function.Function;

public class WeatherFunction {

    public record WeatherRequest(
            @JsonPropertyDescription("城市名称，如：北京") String city,
            @JsonPropertyDescription("日期，格式：yyyy-MM-dd，默认今天") String date
    ) {}

    public record WeatherResponse(
            String city,
            String date,
            String weather,
            String temperature
    ) {}

    @Description("查询指定城市的天气信息")
    public Function<WeatherRequest, WeatherResponse> weatherFunction() {
        return request -> {
            String date = request.date() != null ? request.date() : "今天";
            return new WeatherResponse(
                    request.city(),
                    date,
                    "晴",
                    "25°C"
            );
        };
    }
}
