package org.example.aiengineeringdemo.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Component
public class WeatherTool {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    private static final Map<String, double[]> CITY_COORDINATES = Map.of(
        "new york", new double[]{40.7128, -74.0060},
        "london", new double[]{51.5074, -0.1278},
        "tokyo", new double[]{35.6762, 139.6503},
        "paris", new double[]{48.8566, 2.3522},
        "sydney", new double[]{-33.8688, 151.2093},
        "dubai", new double[]{25.2048, 55.2708},
        "singapore", new double[]{1.3521, 103.8198},
        "berlin", new double[]{52.5200, 13.4050},
        "los angeles", new double[]{34.0522, -118.2437},
        "san francisco", new double[]{37.7749, -122.4194}
    );

    public WeatherTool(RestClient.Builder restClientBuilder, ObjectMapper objectMapper) {
        this.restClient = restClientBuilder
            .baseUrl("https://api.open-meteo.com/v1")
            .build();
        this.objectMapper = objectMapper;
    }

    @Tool(description = "Get current weather information for a city. Returns temperature in Celsius and wind speed.")
    public String getWeatherByCity(
            @ToolParam(description = "Name of the city (e.g., 'New York', 'London', 'Tokyo')") String city) {

        double[] coords = CITY_COORDINATES.get(city.toLowerCase());
        if (coords == null) {
            return "Weather data not available for city: " + city + ". Supported cities: " + String.join(", ", CITY_COORDINATES.keySet());
        }

        return getWeatherByCoordinates(coords[0], coords[1], city);
    }

    @Tool(description = "Get current weather information by latitude and longitude coordinates.")
    public String getWeatherByCoordinates(
            @ToolParam(description = "Latitude of the location") double latitude,
            @ToolParam(description = "Longitude of the location") double longitude) {
        return getWeatherByCoordinates(latitude, longitude, null);
    }

    private String getWeatherByCoordinates(double latitude, double longitude, String cityName) {
        try {
            String response = restClient.get()
                .uri("/forecast?latitude={lat}&longitude={lon}&current=temperature_2m,relative_humidity_2m,wind_speed_10m,weather_code",
                    latitude, longitude)
                .retrieve()
                .body(String.class);

            JsonNode root = objectMapper.readTree(response);
            JsonNode current = root.get("current");

            double temperature = current.get("temperature_2m").asDouble();
            double humidity = current.get("relative_humidity_2m").asDouble();
            double windSpeed = current.get("wind_speed_10m").asDouble();
            int weatherCode = current.get("weather_code").asInt();

            String weatherDescription = getWeatherDescription(weatherCode);
            String location = cityName != null ? cityName : String.format("(%.2f, %.2f)", latitude, longitude);

            return String.format(
                "Weather in %s: %s, Temperature: %.1f°C, Humidity: %.0f%%, Wind Speed: %.1f km/h",
                location, weatherDescription, temperature, humidity, windSpeed
            );
        } catch (Exception e) {
            return "Failed to fetch weather data: " + e.getMessage();
        }
    }

    private String getWeatherDescription(int code) {
        return switch (code) {
            case 0 -> "Clear sky";
            case 1, 2, 3 -> "Partly cloudy";
            case 45, 48 -> "Foggy";
            case 51, 53, 55 -> "Drizzle";
            case 61, 63, 65 -> "Rain";
            case 71, 73, 75 -> "Snow";
            case 77 -> "Snow grains";
            case 80, 81, 82 -> "Rain showers";
            case 85, 86 -> "Snow showers";
            case 95 -> "Thunderstorm";
            case 96, 99 -> "Thunderstorm with hail";
            default -> "Unknown";
        };
    }
}
