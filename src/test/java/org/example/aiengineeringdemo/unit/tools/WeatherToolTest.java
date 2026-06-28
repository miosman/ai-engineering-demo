package org.example.aiengineeringdemo.unit.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.aiengineeringdemo.tools.WeatherTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings({"unchecked", "rawtypes"})
class WeatherToolTest {

    @Mock
    RestClient.Builder restClientBuilder;

    @Mock
    RestClient restClient;

    @Mock
    RestClient.RequestHeadersUriSpec requestSpec;

    @Mock
    RestClient.ResponseSpec responseSpec;

    WeatherTool weatherTool;

    // Matches the JSON structure returned by Open-Meteo
    private static final String WEATHER_JSON = """
        {
          "current": {
            "temperature_2m": 22.5,
            "relative_humidity_2m": 65.0,
            "wind_speed_10m": 15.3,
            "weather_code": 1
          }
        }
        """;

    @BeforeEach
    void setUp() {
        lenient().when(restClientBuilder.baseUrl(anyString())).thenReturn(restClientBuilder);
        lenient().when(restClientBuilder.build()).thenReturn(restClient);
        lenient().when(restClient.get()).thenReturn(requestSpec);
        // uri(String, Object...) with two double args autoboxed to Double
        lenient().doReturn(requestSpec).when(requestSpec).uri(anyString(), any(), any());
        lenient().when(requestSpec.retrieve()).thenReturn(responseSpec);
        lenient().when(responseSpec.body(String.class)).thenReturn(WEATHER_JSON);

        weatherTool = new WeatherTool(restClientBuilder, new ObjectMapper());
    }

    @Test
    void getWeatherByCity_knownCity_returnsFormattedWeather() {
        String result = weatherTool.getWeatherByCity("London");

        assertThat(result).contains("London");
        assertThat(result).contains("22.5°C");
        assertThat(result).contains("65%");
        assertThat(result).contains("15.3 km/h");
        assertThat(result).contains("Partly cloudy");
    }

    @Test
    void getWeatherByCity_unknownCity_returnsErrorWithSupportedCitiesList() {
        String result = weatherTool.getWeatherByCity("Atlantis");

        assertThat(result).contains("Weather data not available for city: Atlantis");
        assertThat(result).contains("Supported cities:");
        verifyNoInteractions(restClient);
    }

    @Test
    void getWeatherByCoordinates_callsApiAndFormatsCoordinates() {
        String result = weatherTool.getWeatherByCoordinates(48.8566, 2.3522);

        assertThat(result).contains("48.86");
        assertThat(result).contains("2.35");
        verify(restClient).get();
    }

    @Test
    void getWeatherByCity_caseInsensitiveLookup_findsCity() {
        String result = weatherTool.getWeatherByCity("NEW YORK");

        // Original casing is preserved in the output; unknown city would skip the API call
        assertThat(result).contains("NEW YORK");
        verify(restClient).get();
    }

    @Test
    void getWeatherByCity_httpFailure_returnsGracefulErrorMessage() {
        when(responseSpec.body(String.class)).thenThrow(new RuntimeException("Connection refused"));

        String result = weatherTool.getWeatherByCity("London");

        assertThat(result).startsWith("Failed to fetch weather data:");
    }
}
