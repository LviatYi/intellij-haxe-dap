package com.intellij.plugins.haxe.ide.completion;

import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public final class HaxeCompletionPerformanceTracker {
  private static final Logger LOG = Logger.getInstance(HaxeCompletionPerformanceTracker.class);
  private static final long SLOW_COMPLETION_THRESHOLD_MS = 800L;
  private static final long SLOW_CONTRIBUTOR_THRESHOLD_MS = 500L;
  private static final ThreadLocal<Deque<InvocationData>> INVOCATIONS = ThreadLocal.withInitial(ArrayDeque::new);

  private HaxeCompletionPerformanceTracker() {
  }

  public static long now() {
    return System.nanoTime();
  }

  public static long elapsedMillis(long startNanos) {
    return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
  }

  public static @NotNull InvocationData startInvocation(@NotNull String label, @NotNull CompletionParameters parameters) {
    InvocationData data = new InvocationData(label, now(), describeLocation(parameters));
    INVOCATIONS.get().push(data);
    return data;
  }

  public static void recordContributor(@NotNull String contributorName, @NotNull CompletionParameters parameters, long startNanos) {
    long elapsedMs = elapsedMillis(startNanos);
    InvocationData data = currentInvocation();
    if (data != null) {
      data.recordContributor(contributorName, elapsedMs);
    }

    if (elapsedMs >= SLOW_CONTRIBUTOR_THRESHOLD_MS) {
      LOG.warn("Slow Haxe completion contributor: " + contributorName
               + " took " + elapsedMs + "ms at " + describeLocation(parameters));
    }
  }

  public static void finishInvocation(@NotNull InvocationData invocation,
                                      @NotNull CompletionParameters parameters,
                                      @NotNull Map<String, Long> stageTimings,
                                      int resultCount) {
    long totalMs = elapsedMillis(invocation.startNanos);
    popInvocation(invocation);

    if (totalMs < SLOW_COMPLETION_THRESHOLD_MS) {
      return;
    }

    StringBuilder message = new StringBuilder();
    message.append("Slow Haxe completion: ")
      .append(invocation.label)
      .append(" took ")
      .append(totalMs)
      .append("ms, results=")
      .append(resultCount)
      .append(", location=")
      .append(invocation.location);

    if (!stageTimings.isEmpty()) {
      message.append(", stages=[");
      boolean first = true;
      for (Map.Entry<String, Long> entry : stageTimings.entrySet()) {
        if (!first) {
          message.append(", ");
        }
        message.append(entry.getKey()).append('=').append(entry.getValue()).append("ms");
        first = false;
      }
      message.append(']');
    }

    List<Map.Entry<String, ContributorTiming>> contributors = new ArrayList<>(invocation.contributorTimings.entrySet());
    contributors.sort(Comparator.comparingLong((Map.Entry<String, ContributorTiming> entry) -> entry.getValue().totalMs).reversed());
    if (!contributors.isEmpty()) {
      message.append(", contributors=[");
      boolean first = true;
      for (Map.Entry<String, ContributorTiming> entry : contributors) {
        if (!first) {
          message.append(", ");
        }
        ContributorTiming timing = entry.getValue();
        message.append(entry.getKey()).append('=').append(timing.totalMs).append("ms");
        if (timing.invocations > 1) {
          message.append('/').append(timing.invocations).append('x');
        }
        first = false;
      }
      message.append(']');
    }

    LOG.warn(message.toString());
  }

  private static @Nullable InvocationData currentInvocation() {
    return INVOCATIONS.get().peek();
  }

  private static void popInvocation(@NotNull InvocationData invocation) {
    Deque<InvocationData> stack = INVOCATIONS.get();
    if (!stack.isEmpty() && stack.peek() == invocation) {
      stack.pop();
    } else {
      stack.remove(invocation);
    }
    if (stack.isEmpty()) {
      INVOCATIONS.remove();
    }
  }

  private static @NotNull String describeLocation(@NotNull CompletionParameters parameters) {
    PsiFile file = parameters.getOriginalFile();
    PsiElement position = parameters.getOriginalPosition();
    if (position == null) {
      position = parameters.getPosition();
    }

    String fileName = file.getName();
    int offset = position != null ? position.getTextOffset() : parameters.getOffset();
    String elementText = position != null ? StringUtil.first(position.getText(), 40, true) : "<null>";
    return fileName + ":" + offset + " text='" + StringUtil.escapeCharCharacters(elementText) + "'";
  }

  public static final class InvocationData {
    private final String label;
    private final long startNanos;
    private final String location;
    private final Map<String, ContributorTiming> contributorTimings = new LinkedHashMap<>();

    private InvocationData(@NotNull String label, long startNanos, @NotNull String location) {
      this.label = label;
      this.startNanos = startNanos;
      this.location = location;
    }

    private void recordContributor(@NotNull String contributorName, long elapsedMs) {
      contributorTimings.computeIfAbsent(contributorName, ignored -> new ContributorTiming()).record(elapsedMs);
    }
  }

  private static final class ContributorTiming {
    private long totalMs;
    private int invocations;

    private void record(long elapsedMs) {
      totalMs += elapsedMs;
      invocations++;
    }
  }
}
