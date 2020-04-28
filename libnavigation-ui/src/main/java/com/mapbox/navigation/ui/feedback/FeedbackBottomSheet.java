package com.mapbox.navigation.ui.feedback;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatButton;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.mapbox.libnavigation.ui.R;
import com.mapbox.navigation.core.telemetry.events.FeedbackEvent;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A BottomSheetDialogFragment shows Feedback UI with different feedback categories.
 * <p>
 * This view takes a {@link FeedbackBottomSheetListener}.
 * The {@link FeedbackBottomSheetListener#onFeedbackSelected(FeedbackItem)} will be called
 * when a feedback category is selected.
 * The {@link FeedbackBottomSheetListener#onFeedbackDismissed()} will be called when this fragment dismiss.
 */
public class FeedbackBottomSheet extends BottomSheetDialogFragment implements Animator.AnimatorListener {

  public static final String TAG = FeedbackBottomSheet.class.getSimpleName();
  public static final int FEEDBACK_MAIN_FLOW = 0;
  public static final int FEEDBACK_DETAIL_FLOW = 1;
  private static final String EMPTY_FEEDBACK_DESCRIPTION = "";
  private static final long CLOSE_BOTTOM_SHEET_AFTER = 150L;
  private static final long TIMER_INTERVAL = 1L;
  private static final int GRID_SPAN_GUIDANCE_LAYOUT = 2;
  private static final int GRID_SPAN_NAVIGATION_LAYOUT = 3;

  private FeedbackBottomSheetListener feedbackBottomSheetListener;
  private TextView feedbackBottomSheetTitleText;
  private ImageButton cancelBtn;
  private LinearLayout feedbackMainLayout;
  private RecyclerView guidanceIssueItems;
  private FeedbackAdapter guidanceIssueAdapter;
  private RecyclerView navigationIssueItems;
  private FeedbackAdapter notificationIssueAdapter;
  private RelativeLayout feedbackDescriptionsLayout;
  private FeedbackDescriptionAdapter feedbackDescriptionAdapter;
  private RecyclerView feedbackDescriptionItems;
  private ProgressBar feedbackProgressBar;
  private ObjectAnimator countdownAnimation;
  private AppCompatButton reportIssueBtn;
  private int feedbackFlowType = FEEDBACK_MAIN_FLOW;
  private long duration;
  private CountDownTimer timer = null;
  private FeedbackItem selectedFeedbackItem;
  private Set<String> selectedFeedbackDescription;

  public static FeedbackBottomSheet newInstance(FeedbackBottomSheetListener feedbackBottomSheetListener,
                                                long duration) {
    return newInstance(feedbackBottomSheetListener, FEEDBACK_MAIN_FLOW, duration);
  }

  public static FeedbackBottomSheet newInstance(FeedbackBottomSheetListener feedbackBottomSheetListener,
                                                int flowType, long duration) {
    FeedbackBottomSheet feedbackBottomSheet = new FeedbackBottomSheet();
    feedbackBottomSheet.feedbackFlowType = flowType;
    feedbackBottomSheet.setFeedbackBottomSheetListener(feedbackBottomSheetListener);
    feedbackBottomSheet.setDuration(duration);
    feedbackBottomSheet.setRetainInstance(true);
    return feedbackBottomSheet;
  }

  @Override
  public void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setStyle(BottomSheetDialogFragment.STYLE_NO_FRAME, R.style.Theme_Design_BottomSheetDialog);
  }

  @Override
  public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    return inflater.inflate(R.layout.mapbox_feedback_bottom_sheet_layout, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);
    bind(view);
    initTitleTextView();
    initButtons();
    initFeedbackRecyclerView();
  }

  @NonNull
  @Override
  public Dialog onCreateDialog(Bundle savedInstanceState) {
    BottomSheetDialog dialog = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);
    dialog.setOnShowListener(new DialogInterface.OnShowListener() {
      @Override
      public void onShow(DialogInterface dialog) {
        BottomSheetDialog bottomSheetDialog = (BottomSheetDialog) dialog;
        FrameLayout bottomSheet = bottomSheetDialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (bottomSheet != null) {
          BottomSheetBehavior<FrameLayout> behavior = BottomSheetBehavior.from(bottomSheet);
          behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
          behavior.setSkipCollapsed(true);
        }
      }
    });
    return dialog;
  }

  @Override
  public void onDismiss(@NotNull DialogInterface dialog) {
    super.onDismiss(dialog);
    feedbackBottomSheetListener.onFeedbackDismissed();
  }

  @Override
  public void onDestroyView() {
    removeListener();
    removeDialogDismissMessage();
    cancelCountdownAnimation();
    super.onDestroyView();
  }

  @Override
  public void onAnimationEnd(Animator animation) {
    FeedbackBottomSheet.this.dismiss();
  }

  //region Unused Listener Methods

  @Override
  public void onAnimationStart(Animator animation) {

  }

  @Override
  public void onAnimationCancel(Animator animation) {

  }

  @Override
  public void onAnimationRepeat(Animator animation) {

  }

  //endregion

  public void setFeedbackBottomSheetListener(FeedbackBottomSheetListener feedbackBottomSheetListener) {
    this.feedbackBottomSheetListener = feedbackBottomSheetListener;
  }

  /**
   * @param duration in milliseconds, the BottomSheet will show before being dismissed.
   */
  public void setDuration(long duration) {
    this.duration = duration;
  }

  private void bind(View bottomSheetView) {
    feedbackBottomSheetTitleText = bottomSheetView.findViewById(R.id.feedbackBottomSheetTitleText);
    cancelBtn = bottomSheetView.findViewById(R.id.cancelBtn);

    feedbackMainLayout = bottomSheetView.findViewById(R.id.feedbackMainLayout);
    guidanceIssueItems = bottomSheetView.findViewById(R.id.guidanceIssueItems);
    navigationIssueItems = bottomSheetView.findViewById(R.id.navigationIssueItems);

    feedbackDescriptionsLayout = bottomSheetView.findViewById(R.id.feedbackDescriptionsLayout);
    feedbackDescriptionItems = bottomSheetView.findViewById(R.id.feedbackDescriptionItems);
    feedbackProgressBar = bottomSheetView.findViewById(R.id.feedbackProgress);

    reportIssueBtn = bottomSheetView.findViewById(R.id.reportIssueBtn);
  }

  private void initTitleTextView() {
    feedbackBottomSheetTitleText.setText(R.string.report_feedback);
  }

  private void initButtons() {
    cancelBtn.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        dismiss();
      }
    });

    reportIssueBtn.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        // TODO: need to adjust code when telemetry API change is ready
        feedbackBottomSheetListener.onFeedbackSelected(selectedFeedbackItem);
        startTimer();
      }
    });
  }

  private void initFeedbackRecyclerView() {
    final Context context = getContext();

    guidanceIssueAdapter = new FeedbackAdapter(buildGuidanceIssueList());
    guidanceIssueItems.setAdapter(guidanceIssueAdapter);
    guidanceIssueItems.setOverScrollMode(RecyclerView.OVER_SCROLL_IF_CONTENT_SCROLLS);
    guidanceIssueItems.addOnItemTouchListener(new FeedbackClickListener(context, guidanceIssueClickCallback));
    guidanceIssueItems.setLayoutManager(new GridLayoutManager(context, GRID_SPAN_GUIDANCE_LAYOUT));

    notificationIssueAdapter = new FeedbackAdapter(buildNavigationIssueList());
    navigationIssueItems.setAdapter(notificationIssueAdapter);
    navigationIssueItems.setOverScrollMode(RecyclerView.OVER_SCROLL_IF_CONTENT_SCROLLS);
    navigationIssueItems.addOnItemTouchListener(new FeedbackClickListener(context, navigationIssueClickCallback));
    navigationIssueItems.setLayoutManager(new GridLayoutManager(context, GRID_SPAN_NAVIGATION_LAYOUT));
  }

  private void initCountDownAnimation() {
    countdownAnimation = ObjectAnimator.ofInt(feedbackProgressBar,
      "progress", 0);
    countdownAnimation.setInterpolator(new LinearInterpolator());
    countdownAnimation.setDuration(duration);
    countdownAnimation.addListener(this);
    countdownAnimation.start();
  }

  private void removeListener() {
    feedbackBottomSheetListener = null;
  }

  private void removeDialogDismissMessage() {
    Dialog dialog = getDialog();
    if (dialog != null && getRetainInstance()) {
      dialog.setDismissMessage(null);
    }
  }

  private void cancelCountdownAnimation() {
    if (countdownAnimation != null) {
      countdownAnimation.removeAllListeners();
      countdownAnimation.cancel();
    }
  }

  private void startTimer() {
    if (timer != null) {
      timer.cancel();
    }
    timer = new CountDownTimer(CLOSE_BOTTOM_SHEET_AFTER, TIMER_INTERVAL) {
      @Override
      public void onTick(long millisUntilFinished) {
        // We don't need to observe changes in interval, hence left empty
      }

      @Override
      public void onFinish() {
        dismiss();
      }
    };
    timer.start();
  }

  @SuppressLint("WrongConstant")
  private List<FeedbackItem> buildGuidanceIssueList() {
    List<FeedbackItem> list = new ArrayList<>();

    list.add(new FeedbackItem(getResources().getString(R.string.feedback_type_incorrect_visual),
      R.drawable.ic_feedback_incorrect_visual,
      FeedbackEvent.INCORRECT_VISUAL_GUIDANCE,
      EMPTY_FEEDBACK_DESCRIPTION));
    list.add(new FeedbackItem(getResources().getString(R.string.feedback_type_confusing_audio),
      R.drawable.ic_feedback_confusing_audio,
      FeedbackEvent.INCORRECT_AUDIO_GUIDANCE,
      EMPTY_FEEDBACK_DESCRIPTION));

    return list;
  }

  private FeedbackClickListener.ClickCallback guidanceIssueClickCallback = new FeedbackClickListener.ClickCallback() {
    @Override
    public void onFeedbackItemClick(ImageView view, int feedbackPosition) {
      if (view != null) {
        view.setPressed(!view.isPressed());
      }
      FeedbackItem feedbackItem = guidanceIssueAdapter.getFeedbackItem(feedbackPosition);
      onFeedbackSelected(feedbackItem);
    }
  };

  @SuppressLint("WrongConstant")
  private List<FeedbackItem> buildNavigationIssueList() {
    List<FeedbackItem> list = new ArrayList<>();

    list.add(new FeedbackItem(getResources().getString(R.string.feedback_type_route_quality),
      R.drawable.ic_feedback_route_quality,
      FeedbackEvent.ROUTING_ERROR,
      EMPTY_FEEDBACK_DESCRIPTION));
    list.add(new FeedbackItem(getResources().getString(R.string.feedback_type_illegal_route),
      R.drawable.ic_feedback_illegal_route,
      FeedbackEvent.NOT_ALLOWED,
      EMPTY_FEEDBACK_DESCRIPTION));
    list.add(new FeedbackItem(getResources().getString(R.string.feedback_type_road_closure),
      R.drawable.ic_feedback_road_closure,
      FeedbackEvent.ROAD_CLOSED,
      EMPTY_FEEDBACK_DESCRIPTION));

    return list;
  }

  private FeedbackClickListener.ClickCallback navigationIssueClickCallback = new FeedbackClickListener.ClickCallback() {
    @Override
    public void onFeedbackItemClick(ImageView view, int feedbackPosition) {
      if (view != null) {
        view.setPressed(!view.isPressed());
      }
      FeedbackItem feedbackItem = notificationIssueAdapter.getFeedbackItem(feedbackPosition);
      onFeedbackSelected(feedbackItem);
    }
  };

  private void onFeedbackSelected(FeedbackItem feedbackItem) {
    if (feedbackFlowType == FEEDBACK_MAIN_FLOW) {
      feedbackBottomSheetListener.onFeedbackSelected(feedbackItem);
      startTimer();
    } else if (feedbackFlowType == FEEDBACK_DETAIL_FLOW) {
      launchDetailFlow(feedbackItem);
    }
  }

  private void launchDetailFlow(@NonNull FeedbackItem feedbackItem) {
    initCountDownAnimation();
    selectedFeedbackItem = feedbackItem;
    selectedFeedbackDescription = new HashSet<>();

    feedbackBottomSheetTitleText.setText(feedbackItem.getFeedbackText().replace('\n', ' '));
    initFeedbackIssueDetailRecyclerView(feedbackItem);
    feedbackMainLayout.setVisibility(View.GONE);
    feedbackDescriptionsLayout.setVisibility(View.VISIBLE);
  }

  private void initFeedbackIssueDetailRecyclerView(FeedbackItem feedbackItem) {
    feedbackDescriptionAdapter = new FeedbackDescriptionAdapter(
      buildFeedbackIssueDetailList(feedbackItem), descriptionItemClickListener);
    feedbackDescriptionItems.setAdapter(feedbackDescriptionAdapter);
    feedbackDescriptionItems.setOverScrollMode(RecyclerView.OVER_SCROLL_ALWAYS);
    feedbackDescriptionItems.setLayoutManager(new LinearLayoutManager(this.getContext()));
  }

  private FeedbackDescriptionAdapter.OnDescriptionItemClickListener descriptionItemClickListener =
    new FeedbackDescriptionAdapter.OnDescriptionItemClickListener() {
      @Override
      public boolean onItemClick(int position) {
        FeedbackDescriptionItem item = feedbackDescriptionAdapter.getFeedbackDescriptionItem(position);
        if (selectedFeedbackDescription.add(item.getFeedbackDescription())) {
          item.setChecked(true);
          return true;
        } else {
          selectedFeedbackDescription.remove(item.getFeedbackDescription());
          item.setChecked(false);
          return false;
        }
      }
    };

  private List<FeedbackDescriptionItem> buildFeedbackIssueDetailList(FeedbackItem feedbackItem) {
    List<FeedbackDescriptionItem> list = new ArrayList<>();
    switch (feedbackItem.getFeedbackType()) {
      case FeedbackEvent.INCORRECT_VISUAL_GUIDANCE:
        list.add(new FeedbackDescriptionItem(
          FeedbackEvent.TURN_ICON_INCORRECT,
          R.string.feedback_description_turn_icon_incorrect));
        list.add(new FeedbackDescriptionItem(
          FeedbackEvent.STREET_NAME_INCORRECT,
          R.string.feedback_description_street_name_incorrect));
        list.add(new FeedbackDescriptionItem(
          FeedbackEvent.INSTRUCTION_UNNECESSARY,
          R.string.feedback_description_instruction_unnecessary));
        list.add(new FeedbackDescriptionItem(
          FeedbackEvent.INSTRUCTION_MISSING,
          R.string.feedback_description_instruction_missing));
        list.add(new FeedbackDescriptionItem(
          FeedbackEvent.MANEUVER_INCORRECT,
          R.string.feedback_description_maneuver_incorrect));
        list.add(new FeedbackDescriptionItem(
          FeedbackEvent.EXIT_INFO_INCORRECT,
          R.string.feedback_description_exit_info_incorrect));
        list.add(new FeedbackDescriptionItem(
          FeedbackEvent.LANE_GUIDANCE_INCORRECT,
          R.string.feedback_description_lane_guidance_incorrect));
        list.add(new FeedbackDescriptionItem(
          FeedbackEvent.ROAD_KNOW_BY_DIFFERENT_NAME,
          R.string.feedback_description_road_know_by_different_name));
        break;
      case FeedbackEvent.INCORRECT_AUDIO_GUIDANCE:
        list.add(new FeedbackDescriptionItem(
          FeedbackEvent.GUIDANCE_TOO_EARLY,
          R.string.feedback_description_guidance_too_early));
        list.add(new FeedbackDescriptionItem(
          FeedbackEvent.GUIDANCE_TOO_LATE,
          R.string.feedback_description_guidance_too_late));
        list.add(new FeedbackDescriptionItem(
          FeedbackEvent.PRONUNCIATION_INCORRECT,
          R.string.feedback_description_pronunciation_incorrect));
        list.add(new FeedbackDescriptionItem(
          FeedbackEvent.ROAD_NAME_REPEATED,
          R.string.feedback_description_road_name_repeated));
        break;
      case FeedbackEvent.ROUTING_ERROR:
        list.add(new FeedbackDescriptionItem(
          FeedbackEvent.ROUTE_NOT_DRIVE_ABLE,
          R.string.feedback_description_route_not_drive_able));
        list.add(new FeedbackDescriptionItem(
          FeedbackEvent.ROUTE_NOT_PREFERRED,
          R.string.feedback_description_route_not_preferred));
        list.add(new FeedbackDescriptionItem(
          FeedbackEvent.ALTERNATIVE_ROUTE_NOT_EXPECTED,
          R.string.feedback_description_alternative_route_not_expected));
        list.add(new FeedbackDescriptionItem(
          FeedbackEvent.ROUTE_INCLUDED_MISSING_ROADS,
          R.string.feedback_description_route_included_missing_roads));
        list.add(new FeedbackDescriptionItem(
          FeedbackEvent.ROUTE_HAD_ROADS_TOO_NARROW_TO_PASS,
          R.string.feedback_description_route_had_roads_too_narrow_to_pass));
        break;
      case FeedbackEvent.NOT_ALLOWED:
        list.add(new FeedbackDescriptionItem(
          FeedbackEvent.ROUTED_DOWN_A_ONE_WAY,
          R.string.feedback_description_routed_down_a_one_way));
        list.add(new FeedbackDescriptionItem(
          FeedbackEvent.TURN_WAS_NOT_ALLOWED,
          R.string.feedback_description_turn_was_not_allowed));
        list.add(new FeedbackDescriptionItem(
          FeedbackEvent.CARS_NOT_ALLOWED_ON_STREET,
          R.string.feedback_description_cars_not_allowed_on_street));
        list.add(new FeedbackDescriptionItem(
          FeedbackEvent.TURN_AT_INTERSECTION_WAS_UNPROTECTED,
          R.string.feedback_description_turn_at_intersection_was_unprotected));
        break;
      case FeedbackEvent.ROAD_CLOSED:
        list.add(new FeedbackDescriptionItem(
          FeedbackEvent.STREET_PERMANENTLY_BLOCKED_OFF,
          R.string.feedback_description_street_permanently_blocked_off));
        list.add(new FeedbackDescriptionItem(
          FeedbackEvent.ROAD_IS_MISSING_FROM_MAP,
          R.string.feedback_description_road_is_missing_from_map));
        break;
      default:
    }
    return list;
  }

}
