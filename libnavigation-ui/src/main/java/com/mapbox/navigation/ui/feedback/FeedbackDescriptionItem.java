package com.mapbox.navigation.ui.feedback;

import androidx.annotation.StringRes;

import com.mapbox.navigation.core.telemetry.events.FeedbackEvent;

class FeedbackDescriptionItem {

  @FeedbackEvent.Description
  private String feedbackDescription;

  @StringRes
  private int feedbackDescriptionResourceId;

  private boolean checked;

  FeedbackDescriptionItem(@FeedbackEvent.Description String feedbackDescription,
                          @StringRes int feedbackDescriptionResourceId) {
    this.feedbackDescription = feedbackDescription;
    this.feedbackDescriptionResourceId = feedbackDescriptionResourceId;
  }

  @FeedbackEvent.Description
  String getFeedbackDescription() {
    return feedbackDescription;
  }

  @StringRes
  int getFeedbackDescriptionResourceId() {
    return feedbackDescriptionResourceId;
  }

  void setChecked(boolean checked) {
    this.checked = checked;
  }

  boolean isChecked() {
    return checked;
  }
}
