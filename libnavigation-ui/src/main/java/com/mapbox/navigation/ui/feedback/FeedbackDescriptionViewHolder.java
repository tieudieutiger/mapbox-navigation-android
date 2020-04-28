package com.mapbox.navigation.ui.feedback;

import android.view.View;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.mapbox.libnavigation.ui.R;

class FeedbackDescriptionViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
  private CheckBox checkBox;
  private TextView feedbackIssueDetail;
  private FeedbackDescriptionAdapter.OnDescriptionItemClickListener itemClickListener;

  FeedbackDescriptionViewHolder(View itemView,
                                FeedbackDescriptionAdapter.OnDescriptionItemClickListener itemClickListener) {
    super(itemView);

    checkBox = itemView.findViewById(R.id.checkbox);
    feedbackIssueDetail = itemView.findViewById(R.id.feedbackIssueDetail);

    this.itemClickListener = itemClickListener;
    itemView.setOnClickListener(this);
    checkBox.setOnClickListener(this);
  }

  void setFeedbackIssueDetail(FeedbackDescriptionItem item) {
    checkBox.setChecked(item.isChecked());
    feedbackIssueDetail.setText(item.getFeedbackDescriptionResourceId());
  }

  @Override
  public void onClick(View view) {
    if (itemClickListener != null) {
      checkBox.setChecked(itemClickListener.onItemClick(getAdapterPosition()));
    }
  }
}
