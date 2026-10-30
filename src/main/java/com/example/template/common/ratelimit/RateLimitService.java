package com.example.template.common.ratelimit;

import java.time.Duration;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.util.AntPathMatcher;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;

/**
 * Decides whether a request may proceed. Knows nothing about HTTP: it takes
 * a path and a client key. All Bucket4j and storage details live in this
 * class, so moving the counters to Redis or PostgreSQL later means changing
 * how buckets are obtained here, and nothing in the filter or the rules.
 *
 * Counters are in memory, per JVM (see README for the consequences).
 */
@Service
public class RateLimitService {

  public record Decision(boolean allowed, long retryAfterSeconds) {

    static final Decision ALLOWED = new Decision(true, 0);

  }

  private final RateLimitProperties properties;
  private final AntPathMatcher pathMatcher = new AntPathMatcher();
  private final List<CompiledRule> rules;

  public RateLimitService(RateLimitProperties properties) {
    this.properties = properties;
    this.rules = properties.getRules().values().stream()
        .map(rule -> new CompiledRule(rule, properties.getMaxTrackedClients())).toList();
  }

  /**
   * The first rule whose patterns match decides; paths with no rule are never
   * limited.
   */
  public Decision check(String path, String clientKey) {
    if (!properties.isEnabled()) {
      return Decision.ALLOWED;
    }
    for (CompiledRule rule : rules) {
      if (rule.matches(path, pathMatcher)) {
        return rule.consume(clientKey);
      }
    }
    return Decision.ALLOWED;
  }

  private static final class CompiledRule {

    private final List<String> paths;
    private final long capacity;
    private final Duration window;
    private final Cache<String, Bucket> buckets;

    CompiledRule(RateLimitProperties.Rule rule, int maxTrackedClients) {
      this.paths = List.copyOf(rule.getPaths());
      this.capacity = rule.getCapacity();
      this.window = rule.getWindow();
      // A bucket idle for a full window has refilled completely, so
      // evicting it is indistinguishable from keeping it: expiry loses
      // nothing, and maximumSize bounds memory.
      this.buckets = Caffeine.newBuilder().maximumSize(maxTrackedClients).expireAfterAccess(window).build();
    }

    boolean matches(String path, AntPathMatcher matcher) {
      return paths.stream().anyMatch(pattern -> matcher.match(pattern, path));
    }

    Decision consume(String clientKey) {
      Bucket bucket = buckets.get(clientKey,
          key -> Bucket.builder().addLimit(limit -> limit.capacity(capacity).refillGreedy(capacity, window)).build());

      ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
      if (probe.isConsumed()) {
        return Decision.ALLOWED;
      }
      long nanos = probe.getNanosToWaitForRefill();
      long seconds = Math.max(1, (nanos + 999_999_999L) / 1_000_000_000L); // round up
      return new Decision(false, seconds);
    }

  }

}
