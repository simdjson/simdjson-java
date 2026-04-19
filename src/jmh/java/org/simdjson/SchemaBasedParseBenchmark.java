package org.simdjson;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.SingleShotTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
public class SchemaBasedParseBenchmark {
    private final SimdJsonParser simdJsonParser = new SimdJsonParser();
    private final ObjectMapper objectMapper = new ObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private byte[][] buffers;

    @Setup(Level.Trial)
    public void setup() throws IOException {
        try (InputStream is = ParseBenchmark.class.getResourceAsStream("/twitter.json")) {
            byte[] allBytes = is.readAllBytes();
            // Reserialize into individual JSON byte arrays to simulate the use case of
            // deserializing a large number of small (1-2 KB) JSON messages.
            JsonNode statusesWrapper = objectMapper.readTree(allBytes);
            JsonNode statusesArray = statusesWrapper.get("statuses");

            buffers = new byte[statusesArray.size()][];

            for (int i = 0; i < statusesArray.size(); i++) {
                JsonNode status = statusesArray.get(i);
                byte[] buffer = objectMapper.writeValueAsBytes(status);
                buffers[i] = buffer;
            }
        }
        System.out.println("VectorSpecies = " + VectorUtils.BYTE_SPECIES);
        System.out.println("Number of messages = " + buffers.length);
    }

    @Benchmark
    public void simdjson(Blackhole blackhole) {
        for (byte[] buffer : buffers) {
            blackhole.consume(simdJsonParser.parse(buffer, buffer.length, Status.class));
        }
    }

    @Benchmark
    public void jackson(Blackhole blackhole) throws IOException {
        for (byte[] buffer : buffers) {
            blackhole.consume(objectMapper.readValue(buffer, Status.class));
        }
    }

    // objects in twitter.json
    public record Status(
        Metadata metadata,
        String created_at,
        long id,
        String id_str,
        String text,
        String source,
        boolean truncated,
        Long in_reply_to_status_id,
        String in_reply_to_status_id_str,
        Long in_reply_to_user_id,
        String in_reply_to_user_id_str,
        String in_reply_to_screen_name,
        User user,
        Object geo,
        Object coordinates,
        Object place,
        Object contributors,
        // cannot handle recursion
        // Status retweeted_status,
        long retweet_count,
        long favorite_count,
        Entities entities,
        boolean favorited,
        boolean retweeted,
        Boolean possibly_sensitive,
        String lang
    ) {
        public record Metadata(
            String result_type,
            String iso_language_code
        ) {}

        public record User(
            long id,
            String id_str,
            String name,
            String screen_name,
            String location,
            String description,
            String url,
            UserEntities entities,
            // cannot parse Java keyword
            // boolean protected,
            long followers_count,
            long friends_count,
            long listed_count,
            String created_at,
            long favourites_count,
            Integer utc_offset,
            String time_zone,
            boolean geo_enabled,
            boolean verified,
            long statuses_count,
            String lang,
            boolean contributors_enabled,
            boolean is_translator,
            boolean is_translation_enabled,
            String profile_background_color,
            String profile_background_image_url,
            String profile_background_image_url_https,
            boolean profile_background_tile,
            String profile_image_url,
            String profile_image_url_https,
            String profile_banner_url,
            String profile_link_color,
            String profile_sidebar_border_color,
            String profile_sidebar_fill_color,
            String profile_text_color,
            boolean profile_use_background_image,
            boolean default_profile,
            boolean default_profile_image,
            boolean following,
            boolean follow_request_sent,
            boolean notifications
        ) {}

        public record UserEntities(
            UrlList url,
            UrlList description
        ) {}

        public record Entities(
            List<Hashtag> hashtags,
            List<Object> symbols,
            List<UrlEntity> urls,
            List<UserMention> user_mentions,
            List<MediaEntity> media
        ) {}

        public record UrlList(
            List<UrlEntity> urls
        ) {}

        public record Hashtag(
            String text,
            List<Integer> indices
        ) {}

        public record UrlEntity(
            String url,
            String expanded_url,
            String display_url,
            List<Integer> indices
        ) {}

        public record UserMention(
            String screen_name,
            String name,
            long id,
            String id_str,
            List<Integer> indices
        ) {}

        public record MediaEntity(
            long id,
            String id_str,
            List<Integer> indices,
            String media_url,
            String media_url_https,
            String url,
            String display_url,
            String expanded_url,
            String type,
            MediaSizes sizes,
            Long source_status_id,
            String source_status_id_str
        ) {}

        public record MediaSizes(
            MediaSize medium,
            MediaSize small,
            MediaSize thumb,
            MediaSize large
        ) {}

        public record MediaSize(
            int w,
            int h,
            String resize
        ) {}
    }
}
