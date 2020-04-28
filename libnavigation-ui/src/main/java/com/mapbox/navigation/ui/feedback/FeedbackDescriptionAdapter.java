package com.mapbox.navigation.ui.feedback;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.mapbox.libnavigation.ui.R;

import java.util.List;

/**
 * FeedbackDescriptionAdapter provides a binding from {@link FeedbackBottomSheet} data set
 * to {@link FeedbackViewHolder} that are displayed within a {@link RecyclerView}.
 */
public class FeedbackDescriptionAdapter extends RecyclerView.Adapter<FeedbackDescriptionViewHolder> {

  private List<FeedbackDescriptionItem> feedbackDescriptionItems;
  private OnDescriptionItemClickListener itemClickListener;

  FeedbackDescriptionAdapter(List<FeedbackDescriptionItem> feedbackDescriptionItems,
                             OnDescriptionItemClickListener itemClickListener) {
    this.feedbackDescriptionItems = feedbackDescriptionItems;
    this.itemClickListener = itemClickListener;
  }

  @NonNull
  @Override
  public FeedbackDescriptionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
    return new FeedbackDescriptionViewHolder(
      LayoutInflater.from(parent.getContext())
        .inflate(R.layout.mapbox_feedback_detail_viewholder_layout, parent, false), itemClickListener);
  }

  @Override
  public void onBindViewHolder(@NonNull FeedbackDescriptionViewHolder holder, int position) {
    holder.setFeedbackIssueDetail(feedbackDescriptionItems.get(position));
  }

  @Override
  public int getItemCount() {
    return feedbackDescriptionItems.size();
  }

  FeedbackDescriptionItem getFeedbackDescriptionItem(int position) {
    return feedbackDescriptionItems.get(position);
  }

  interface OnDescriptionItemClickListener {
    boolean onItemClick(int position);
  }
}
