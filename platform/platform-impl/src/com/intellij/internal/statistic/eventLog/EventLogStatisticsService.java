// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog;

import com.intellij.internal.statistic.connect.StatServiceException;
import com.intellij.internal.statistic.connect.StatisticsResult;
import com.intellij.internal.statistic.connect.StatisticsResult.ResultCode;
import com.intellij.internal.statistic.connect.StatisticsService;
import com.intellij.internal.statistic.persistence.UsageStatisticsPersistenceComponent;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.StreamUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

public class EventLogStatisticsService implements StatisticsService {
  private static final Logger LOG = Logger.getInstance("com.intellij.internal.statistic.eventLog.EventLogStatisticsService");

  private static final EventLogStatisticsSettingsService mySettingsService = EventLogStatisticsSettingsService.getInstance();

  @Override
  public StatisticsResult send() {
    if (!FeatureUsageLogger.INSTANCE.isEnabled()) {
      throw new StatServiceException("Event Log collector is not enabled");
    }

    final String serviceUrl = mySettingsService.getServiceUrl();
    if (serviceUrl == null) {
      return new StatisticsResult(StatisticsResult.ResultCode.ERROR_IN_CONFIG, "ERROR: unknown Statistics Service URL.");
    }

    if (!mySettingsService.isTransmissionPermitted()) {
      return new StatisticsResult(StatisticsResult.ResultCode.NOT_PERMITTED_SERVER, "NOT_PERMITTED");
    }

    try {
      int succeed = 0;
      final List<File> logs = FeatureUsageLogger.INSTANCE.getLogFiles();
      final List<File> toRemove = new ArrayList<>(logs.size());
      for (File file : logs) {
        final List<LogEventContent> contents = LogEventContent.Companion.create(file);
        final String error = validate(contents, file);
        if (StringUtil.isNotEmpty(error)) {
          if (LOG.isTraceEnabled()) {
            LOG.trace(error);
          }
          toRemove.add(file);
          continue;
        }

        int succeedBlocks = 0;
        int wrongFormatBlocks = 0;
        for (LogEventContent content : contents) {
          final HttpClient httpClient = HttpClientBuilder.create().build();
          final HttpPost post = createPostRequest(serviceUrl, LogEventSerializer.INSTANCE.toString(content));
          final HttpResponse response = httpClient.execute(post);

          final int code = response.getStatusLine().getStatusCode();
          if (code == HttpStatus.SC_OK) {
            succeedBlocks++;
          }
          else if (code == HttpStatus.SC_BAD_REQUEST) {
            wrongFormatBlocks++;
          }

          if (LOG.isTraceEnabled()) {
            LOG.trace(getResponseMessage(response));
          }
        }

        if (succeedBlocks == contents.size()) {
          succeed++;
        }

        if (succeedBlocks > 0 || wrongFormatBlocks > 0) {
          toRemove.add(file);
        }
      }

      cleanupSentFiles(toRemove);

      UsageStatisticsPersistenceComponent.getInstance().setEventLogSentTime(System.currentTimeMillis());
      if (logs.isEmpty()) {
        return new StatisticsResult(ResultCode.NOTHING_TO_SEND, "No files to upload.");
      }
      else if (succeed != logs.size()) {
        return new StatisticsResult(ResultCode.SENT_WITH_ERRORS, "Uploaded " + succeed + " out of " + logs.size() + " files.");
      }
      return new StatisticsResult(ResultCode.SEND, "Uploaded " + succeed + " files.");
    }
    catch (Exception e) {
      LOG.info(e);
      throw new StatServiceException("Error during data sending.", e);
    }
  }

  @Nullable
  private static String validate(@NotNull List<LogEventContent> contents, @NotNull File file) {
    if (contents.isEmpty()) {
      return "File is empty or has invalid format: " + file.getName();
    }

    for (LogEventContent content : contents) {
      if (content.getEvents().isEmpty()) {
        return "Cannot upload event log, event list is empty";
      }
      else if (StringUtil.isEmpty(content.getUser())) {
        return "Cannot upload event log, user ID is empty";
      }
      else if (StringUtil.isEmpty(content.getProduct())) {
        return "Cannot upload event log, product code is empty";
      }
    }
    return null;
  }

  public void cleanupSentFiles(@NotNull List<File> toRemove) {
    for (File file : toRemove) {
      if (!file.delete()) {
        LOG.warn("Failed deleting event log: " + file.getName());
      }

      if (LOG.isTraceEnabled()) {
        LOG.trace("Removed sent log: " + file.getName());
      }
    }
  }

  @NotNull
  public static HttpPost createPostRequest(@NotNull String serviceUrl, @NotNull String content) throws UnsupportedEncodingException {
    final HttpPost post = new HttpPost(serviceUrl);
    final StringEntity postingString = new StringEntity(content);
    post.setEntity(postingString);
    post.setHeader("Content-type", "application/json");
    return post;
  }

  @Override
  public Notification createNotification(@NotNull String groupDisplayId, @Nullable NotificationListener listener) {
    return null;
  }

  @NotNull
  private static String getResponseMessage(HttpResponse response) throws IOException {
    HttpEntity entity = response.getEntity();
    if (entity != null) {
      return StreamUtil.readText(entity.getContent(), CharsetToolkit.UTF8);
    }
    return Integer.toString(response.getStatusLine().getStatusCode());
  }
}
