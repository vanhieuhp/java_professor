package dev.hieunv.outboxpattern.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;

@Slf4j
public class JsonParserUtils {
	//private static ObjectWriter ow = new ObjectMapper().writer().withDefaultPrettyPrinter();
	private static ObjectReader or = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false).reader();
	public static Gson gson = new GsonBuilder().setPrettyPrinting().create();

	private static final ObjectMapper objectMapper = new ObjectMapper();
	public static String parseObjectToString(Object object) {
		try {
			return gson.toJson(object);
		} catch (Exception e) {
			log.error(e.getLocalizedMessage(), e);
			return "";
		}
	}

	public static <T> T parseStringToObject(String json, Class<T> classObject) {
		try {
			return or.readValue(json, classObject);
		} catch (Exception e) {
			return null;
		}
	}

	public static <T> T parseStringToObjectByGson(String json, Class<T> classObject) {
		try {

			return gson.fromJson(json, classObject);
		} catch (Exception e) {
			return null;
		}
	}

	public static String mapToJson(Object obj) throws JsonProcessingException {
		ObjectMapper objectMapper = new ObjectMapper();
		return objectMapper.writeValueAsString(obj);
	}

	public static <T> T mapToObject(String json, Class<T> clazz) throws JsonProcessingException {
		ObjectMapper objectMapper = new ObjectMapper();
		return objectMapper.readValue(json, clazz);
	}
	public static <T> List<T> mapToListObject(String jsonArray, Class<T> elementType) {
		List<T> response = null;
		try {
			response = objectMapper.readValue(jsonArray,
					objectMapper.getTypeFactory().constructCollectionType(List.class, elementType));
		} catch (JsonProcessingException e) {
			log.error(e.getLocalizedMessage(), e);
		}
		return response;
	}
	// Convert JSON String to List<String>
    public static List<String> jsonToList(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to convert JSON to list", e);
        }
    }

    public static Map<String, Object> fromJsonToMap(String json) {
        try {
            if (json == null || json.isBlank()) return null;
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            throw new RuntimeException("Không thể parse JSON thành Map", e);
        }
    }
}
