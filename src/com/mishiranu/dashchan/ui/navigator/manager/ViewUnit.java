package com.mishiranu.dashchan.ui.navigator.manager;

import android.animation.Animator;
import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.content.res.TypedArray;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.SystemClock;
import android.text.Layout;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.style.BackgroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.TypefaceSpan;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import chan.content.Chan;
import chan.util.StringUtils;
import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.content.ImageLoader;
import com.mishiranu.dashchan.content.Preferences;
import com.mishiranu.dashchan.content.model.AttachmentItem;
import com.mishiranu.dashchan.content.model.GalleryItem;
import com.mishiranu.dashchan.content.model.Post;
import com.mishiranu.dashchan.content.model.PostItem;
import com.mishiranu.dashchan.content.model.PostNumber;
import com.mishiranu.dashchan.graphics.ColorScheme;
import com.mishiranu.dashchan.text.style.LinkSpan;
import com.mishiranu.dashchan.text.style.LinkSuffixSpan;
import com.mishiranu.dashchan.ui.gallery.GalleryOverlay;
import com.mishiranu.dashchan.ui.posting.Replyable;
import com.mishiranu.dashchan.util.AnimationUtils;
import com.mishiranu.dashchan.util.GraphicsUtils;
import com.mishiranu.dashchan.util.ListViewUtils;
import com.mishiranu.dashchan.util.PostDateFormatter;
import com.mishiranu.dashchan.util.ResourceUtils;
import com.mishiranu.dashchan.util.ViewUtils;
import com.mishiranu.dashchan.widget.AttachmentView;
import com.mishiranu.dashchan.widget.CardView;
import com.mishiranu.dashchan.widget.CommentTextView;
import com.mishiranu.dashchan.widget.LinebreakLayout;
import com.mishiranu.dashchan.widget.PostLinearLayout;
import com.mishiranu.dashchan.widget.ThemeEngine;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

public class ViewUnit {
	private final UiManager uiManager;
	private final PostDateFormatter postDateFormatter;
	private final List<CommentTextView.ExtraButton> extraButtons;

	private final int thumbnailWidth;
	private final int multipleAttachmentInfoWidth;

	private final int commentMaxLines;
	private final int commentAdditionalHeight;

	private static final float ALPHA_HIDDEN_POST = 0.2f;
	private static final float ALPHA_DELETED_POST = 0.5f;

	@SuppressLint("InflateParams")
	ViewUnit(UiManager uiManager) {
		Context context = uiManager.getContext();
		this.uiManager = uiManager;
		postDateFormatter = new PostDateFormatter(context);

		extraButtons = Arrays
				.asList(new CommentTextView.ExtraButton(context.getString(R.string.quote__verb),
						R.attr.iconActionPaste, (view, text, click) -> {
					PostViewHolder holder = ListViewUtils.getViewHolder(view, PostViewHolder.class);
					if (holder.configurationSet.replyable != null &&
							holder.configurationSet.replyable.onRequestReply(false)) {
						if (click) {
							holder.configurationSet.replyable.onRequestReply(true, new Replyable
									.ReplyData(holder.postItem.getPostNumber(), text.toPreparedString(view)));
						}
						return true;
					}
					return false;
				}), new CommentTextView.ExtraButton(context.getString(R.string.web_browser),
						R.attr.iconActionForward, (view, text, click) -> {
					Uri uri = extractUri(text.toString());
					if (uri != null) {
						if (click) {
							PostViewHolder holder = ListViewUtils.getViewHolder(view, PostViewHolder.class);
							CommentTextView.LinkListener linkListener = holder.configurationSet.linkListener != null
									? holder.configurationSet.linkListener : defaultLinkListener;
							linkListener.onLinkClick(view, uri, CommentTextView.LinkListener.Extra.EMPTY, true);
						}
						return true;
					}
					return false;
				}), new CommentTextView.ExtraButton(context.getString(R.string.add_theme),
						R.attr.iconActionAddRule, (view, text, click) -> {
					ThemeEngine.Theme theme = ThemeEngine.fastParseThemeFromText(context, text.toString());
					if (theme != null) {
						if (click) {
							uiManager.navigator().navigateSetTheme(theme);
						}
						return true;
					}
					return false;
				}));

		Configuration configuration = context.getResources().getConfiguration();
		float density = ResourceUtils.obtainDensity(context);
		// Define header height, image width and max comment field height
		View view = LayoutInflater.from(context).inflate(R.layout.list_item_post, null);
		View head = view.findViewById(R.id.head);
		TextView comment = view.findViewById(R.id.comment);
		View bottomBar = view.findViewById(R.id.bottom_bar);
		ViewUtils.applyScaleSize(head.findViewById(R.id.subject), head.findViewById(R.id.number),
				head.findViewById(R.id.name), head.findViewById(R.id.index), head.findViewById(R.id.date),
				head.findViewById(R.id.attachment_info), comment, bottomBar.findViewById(R.id.bottom_bar_replies),
				bottomBar.findViewById(R.id.bottom_bar_expand), bottomBar.findViewById(R.id.bottom_bar_open_thread));
		int widthMeasureSpec = View.MeasureSpec.makeMeasureSpec((int) (320 * density + 0.5f), View.MeasureSpec.AT_MOST);
		int heightMeasureSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
		view.measure(widthMeasureSpec, heightMeasureSpec);
		commentMaxLines = Preferences.getPostMaxLines();
		commentAdditionalHeight = bottomBar.getMeasuredHeight();
		thumbnailWidth = head.getMeasuredHeight();
		int additionalAttachmentInfoWidthDp = 64; // approximately equal to thumbnail width + right padding
		int minAttachmentInfoWidthDp = additionalAttachmentInfoWidthDp + 68;
		int maxAttachmentInfoWidthDp = additionalAttachmentInfoWidthDp + 84;
		int attachmentInfoWidthDp = configuration.smallestScreenWidthDp * minAttachmentInfoWidthDp / 320;
		attachmentInfoWidthDp = Math.max(Math.min(attachmentInfoWidthDp, maxAttachmentInfoWidthDp),
				minAttachmentInfoWidthDp);
		attachmentInfoWidthDp -= additionalAttachmentInfoWidthDp;
		multipleAttachmentInfoWidth = (int) (attachmentInfoWidthDp * density + 0.5f);
	}

	private static Uri extractUri(String text) {
		String fixedText = StringUtils.fixParsedUriString(text);
		if (text.equals(fixedText)) {
			if (!text.matches("[a-z]+:.*")) {
				text = "http://" + text.replaceAll("^/+", "");
			}
			Uri uri = Uri.parse(text);
			if (uri != null) {
				if (StringUtils.isEmpty(uri.getAuthority())) {
					uri = uri.buildUpon().scheme("http").build();
				}
				String host = uri.getHost();
				if (host != null && host.matches(".+\\..+") && Chan.getFallback().locator.isWebScheme(uri)) {
					return uri;
				}
			}
		}
		return null;
	}

	private final CommentTextView.LinkListener defaultLinkListener = new CommentTextView.LinkListener() {
		@Override
		public void onLinkClick(CommentTextView view, Uri uri, Extra extra, boolean confirmed) {
			UiManager.Holder holder = ListViewUtils.getViewHolder(view, UiManager.Holder.class);
			uiManager.interaction().handleLinkClick(holder.getConfigurationSet(), uri, extra, confirmed);
		}

		@Override
		public void onLinkLongClick(CommentTextView view, Uri uri, Extra extra) {
			uiManager.interaction().handleLinkLongClick(uri);
		}
	};

	private void onSpanStateChanged(CommentTextView view) {
		uiManager.sendPostItemMessage(view, UiManager.Message.INVALIDATE_COMMENT_VIEW);
	}

	private final CommentTextView.SpanStateListener spanStateListener = this::onSpanStateChanged;

	private final CommentTextView.PrepareToCopyListener prepareToCopyListener =
			(view, text, start, end) -> InteractionUnit.getCopyReadyComment(text, start, end);

	public RecyclerView.ViewHolder createThreadViewHolder(ViewGroup parent,
			UiManager.ConfigurationSet configurationSet, boolean card, boolean cell) {
		return new ThreadViewHolder(parent, configurationSet, uiManager, cell ? ThreadViewHolder.ViewType.CELL
				: card ? ThreadViewHolder.ViewType.CARD : ThreadViewHolder.ViewType.LIST);
	}

	public void bindThreadView(RecyclerView.ViewHolder viewHolder, PostItem postItem) {
		Context context = uiManager.getContext();
		ColorScheme colorScheme = ThemeEngine.getColorScheme(context);
		ThreadViewHolder holder = (ThreadViewHolder) viewHolder;
		Chan chan = Chan.get(holder.configurationSet.chanName);
		holder.postItem = postItem;

		boolean bumpLimitReached = postItem.getBumpLimitReachedState(chan, 0) == PostItem.BumpLimitState.REACHED;
		PostState.Predicate.Data stateData = new PostState.Predicate
				.Data(postItem, holder.configurationSet, bumpLimitReached);
		for (int i = 0; i < PostState.THREAD_ITEM_STATES.size(); i++) {
			boolean visible = PostState.THREAD_ITEM_STATES.get(i).predicate.apply(stateData);
			holder.stateImages[i].setVisibility(visible ? View.VISIBLE : View.GONE);
		}

		String subject = postItem.getSubject();
		if (!StringUtils.isEmpty(subject)) {
			holder.subject.setVisibility(View.VISIBLE);
			holder.subject.setText(subject);
		} else {
			holder.subject.setVisibility(View.GONE);
		}
		int parentWidth = (int) (ResourceUtils.obtainDensity(holder.itemView) *
				holder.itemView.getResources().getConfiguration().screenWidthDp);
		CharSequence comment = postItem.getThreadCommentShort(parentWidth, holder.comment.getTextSize(), 8);
		colorScheme.apply(postItem.getThreadCommentShortSpans());
		if (StringUtils.isEmpty(subject) && StringUtils.isEmpty(comment)) {
			// Avoid 0 height
			comment = " ";
		}
		holder.comment.setText(comment);
		holder.comment.setVisibility(holder.comment.getText().length() > 0 ? View.VISIBLE : View.GONE);
		holder.description.setText(postItem.formatThreadCardDescription(context, false));

		List<AttachmentItem> attachmentItems = postItem.getAttachmentItems();
		if (attachmentItems != null) {
			AttachmentItem attachmentItem = attachmentItems.get(0);
			boolean needShowMultipleIcon = attachmentItems.size() > 1;
			attachmentItem.configureAndLoad(holder.thumbnail, chan, needShowMultipleIcon, false);
			holder.thumbnailClickListener.update(0, true, GalleryOverlay.NavigatePostMode.DISABLED);
			holder.thumbnailLongClickListener.update(attachmentItem);
			holder.thumbnail.setSfwMode(Preferences.isSfwMode());
			holder.thumbnail.setVisibility(View.VISIBLE);
		} else {
			ImageLoader.getInstance().cancel(holder.thumbnail);
			holder.thumbnail.resetImage(null);
			holder.thumbnail.setVisibility(View.GONE);
		}
		holder.thumbnail.setOnClickListener(holder.thumbnailClickListener);
		holder.thumbnail.setOnLongClickListener(holder.thumbnailLongClickListener);
	}

	public void bindThreadCellView(RecyclerView.ViewHolder viewHolder, PostItem postItem,
			boolean small, int contentHeight) {
		Context context = uiManager.getContext();
		ColorScheme colorScheme = ThemeEngine.getColorScheme(context);
		ThreadViewHolder holder = (ThreadViewHolder) viewHolder;
		Chan chan = Chan.get(holder.configurationSet.chanName);
		holder.postItem = postItem;

		List<AttachmentItem> attachmentItems = postItem.getAttachmentItems();
		boolean hidden = postItem.getHideState().hidden;
		((View) holder.threadContent.getParent()).setAlpha(hidden ? ALPHA_HIDDEN_POST : 1f);
		String subject = postItem.getSubject();
		if (!StringUtils.isEmptyOrWhitespace(subject) && !hidden) {
			holder.subject.setVisibility(View.VISIBLE);
			holder.subject.setSingleLine(!small);
			SpannableStringBuilder builder = new SpannableStringBuilder(subject.trim());
			if (!small) {
				builder.setSpan(new RelativeSizeSpan(4f / 3f), 0, builder.length(),
						SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE);
				builder.setSpan(new TypefaceSpan("sans-serif-light"), 0, builder.length(),
						SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE);
			}
			holder.subject.setText(builder);
		} else {
			holder.subject.setVisibility(View.GONE);
		}
		CharSequence comment = null;
		if (hidden) {
			comment = postItem.getHideReason();
		} else if (!small || attachmentItems == null) {
			int parentWidth = (int) (ResourceUtils.obtainDensity(holder.itemView) *
					holder.itemView.getResources().getConfiguration().screenWidthDp);
			comment = postItem.getThreadCommentShort(parentWidth / 2,
					holder.comment.getTextSize(), attachmentItems != null ? 4 : 12);
			colorScheme.apply(postItem.getThreadCommentShortSpans());
		}
		holder.comment.setText(comment);
		holder.comment.setVisibility(StringUtils.isEmpty(comment) ? View.GONE : View.VISIBLE);
		holder.description.setText(postItem.formatThreadCardDescription(context, true));

		if (attachmentItems != null && !hidden) {
			AttachmentItem attachmentItem = attachmentItems.get(0);
			boolean needShowMultipleIcon = attachmentItems.size() > 1;
			attachmentItem.configureAndLoad(holder.thumbnail, chan, needShowMultipleIcon, false);
			holder.thumbnailClickListener.update(0, true, GalleryOverlay.NavigatePostMode.DISABLED);
			holder.thumbnailLongClickListener.update(attachmentItem);
			holder.thumbnail.setSfwMode(Preferences.isSfwMode());
			holder.thumbnail.setVisibility(View.VISIBLE);
		} else {
			ImageLoader.getInstance().cancel(holder.thumbnail);
			holder.thumbnail.resetImage(null);
			holder.thumbnail.setVisibility(View.GONE);
		}
		holder.thumbnail.setOnClickListener(holder.thumbnailClickListener);
		holder.thumbnail.setOnLongClickListener(holder.thumbnailLongClickListener);

		holder.threadContent.getLayoutParams().height = contentHeight;
	}

	public RecyclerView.ViewHolder createThreadHiddenView(ViewGroup parent,
			UiManager.ConfigurationSet configurationSet, boolean card) {
		return new HiddenViewHolder(parent, configurationSet, card, true);
	}

	public void bindThreadHiddenView(RecyclerView.ViewHolder viewHolder, PostItem postItem) {
		HiddenViewHolder holder = (HiddenViewHolder) viewHolder;
		holder.postItem = postItem;
		String description = postItem.getHideReason();
		if (description == null) {
			description = postItem.getSubjectOrComment();
		}
		holder.comment.setText(description);
	}

	public RecyclerView.ViewHolder createPostView(ViewGroup parent,
			UiManager.ConfigurationSet configurationSet) {
		return new PostViewHolder(parent, configurationSet, uiManager, thumbnailWidth);
	}

	public void bindPostView(RecyclerView.ViewHolder viewHolder, PostItem postItem, UiManager.DemandSet demandSet) {
		ColorScheme colorScheme = ThemeEngine.getColorScheme(uiManager.getContext());
		PostViewHolder holder = (PostViewHolder) viewHolder;
		Chan chan = Chan.get(holder.configurationSet.chanName);
		holder.resetAnimations();
		holder.postItem = postItem;
		holder.selection = demandSet.selection;

		String boardName = postItem.getBoardName();
		String threadNumber = postItem.getThreadNumber();
		PostNumber postNumber = postItem.getPostNumber();
		boolean bumpLimitReached = false;
		PostItem.BumpLimitState bumpLimitReachedState = postItem.getBumpLimitReachedState(chan, 0);
		if (bumpLimitReachedState == PostItem.BumpLimitState.REACHED) {
			bumpLimitReached = true;
		} else if (bumpLimitReachedState == PostItem.BumpLimitState.NEED_COUNT &&
				holder.configurationSet.postsProvider != null) {
			int postsCount = 0;
			for (PostItem itPostItem : holder.configurationSet.postsProvider) {
				if (!itPostItem.isDeleted()) {
					postsCount++;
				}
			}
			bumpLimitReached = postItem.getBumpLimitReachedState(chan, postsCount) == PostItem.BumpLimitState.REACHED;
		}
		holder.number.setText("#" + postNumber);
		PostState.Predicate.Data stateData = new PostState.Predicate
				.Data(postItem, holder.configurationSet, bumpLimitReached);
		for (int i = 0; i < PostState.POST_ITEM_STATES.size(); i++) {
			boolean visible = PostState.POST_ITEM_STATES.get(i).predicate.apply(stateData);
			holder.stateImages[i].setVisibility(visible ? View.VISIBLE : View.GONE);
		}
		viewHolder.itemView.setAlpha(postItem.isDeleted() ? ALPHA_DELETED_POST : 1f);

		CharSequence name = postItem.getFullName(chan);
		colorScheme.apply(postItem.getFullNameSpans());
		holder.name.setText(makeHighlightedText(demandSet.highlightText, name));
		holder.date.setText(postItem.getDateTime(postDateFormatter));

		String subject = postItem.getSubject();
		CharSequence comment = holder.configurationSet.repliesToPost != null
				? postItem.getComment(chan, holder.configurationSet.repliesToPost) : postItem.getComment(chan);
				colorScheme.apply(postItem.getCommentSpans());
		LinkSuffixSpan[] linkSuffixSpans = postItem.getLinkSuffixSpansAfterComment();
		if (linkSuffixSpans != null) {
			boolean showMyPosts = Preferences.isShowMyPosts();
			for (LinkSuffixSpan span : linkSuffixSpans) {
				span.setSuffix(LinkSuffixSpan.SUFFIX_USER_POST, showMyPosts &&
						holder.configurationSet.postStateProvider.isUserPost(span.getPostNumber()));
			}
		}
		LinkSpan[] linkSpans = postItem.getLinkSpansAfterComment();
		if (linkSpans != null) {
			for (LinkSpan linkSpan : linkSpans) {
				if (linkSpan.postNumber != null) {
					boolean hidden = false;
					if (postItem.getReferencesTo().contains(linkSpan.postNumber)
							&& holder.configurationSet.postsProvider != null) {
						PostItem linkPostItem = holder.configurationSet.postsProvider.findPostItem(linkSpan.postNumber);
						if (linkPostItem != null) {
							hidden = holder.configurationSet.postStateProvider.isHiddenResolve(linkPostItem);
						}
					}
					linkSpan.setHidden(hidden);
				}
			}
		}
		holder.comment.setSpoilersEnabled(!Preferences.isShowSpoilers());
		holder.comment.setSubjectAndComment(makeHighlightedText(demandSet.highlightText, subject),
				makeHighlightedText(demandSet.highlightText, comment));
		holder.comment.setLinkListener(holder.configurationSet.linkListener != null
				? holder.configurationSet.linkListener : defaultLinkListener,
				holder.configurationSet.chanName, boardName, threadNumber);
		holder.comment.setVisibility(subject.length() > 0 || comment.length() > 0 ? View.VISIBLE : View.GONE);
		holder.comment.bindSelectionPaddingView(demandSet.lastInList ? holder.textSelectionPadding : null);

		handlePostViewIcons(holder);
		handlePostViewAttachments(holder);
		holder.index.setText(postItem.getOrdinalIndexString());
		boolean showName = holder.thumbnail.getVisibility() == View.VISIBLE ||
				!postItem.isUseDefaultName() && !StringUtils.isEmpty(name);
		holder.name.setVisibility(showName ? View.VISIBLE : View.GONE);
		boolean showIndex = postItem.getOrdinalIndex() != PostItem.ORDINAL_INDEX_NONE;
		holder.index.setVisibility(showIndex ? View.VISIBLE : View.GONE);

		if (demandSet.selection == UiManager.Selection.THREADSHOT) {
			holder.bottomBarReplies.setVisibility(View.GONE);
			holder.bottomBarExpand.setVisibility(View.GONE);
			holder.bottomBarOpenThread.setVisibility(View.GONE);
		} else {
			int replyCount = postItem.getPostReplyCount();
			if (postItem.getPostReplyCount() > 0) {
				holder.bottomBarReplies.setText(holder.itemView.getResources().getQuantityString
						(R.plurals.number_replies__format, replyCount, replyCount));
				holder.bottomBarReplies.setVisibility(View.VISIBLE);
			} else {
				holder.bottomBarReplies.setVisibility(View.GONE);
			}
			holder.bottomBarExpand.setVisibility(View.GONE);
			holder.bottomBarOpenThread.setVisibility(demandSet.showOpenThreadButton ? View.VISIBLE : View.GONE);
		}
		if (holder.configurationSet.mayCollapse && commentMaxLines > 0 &&
				!holder.configurationSet.postStateProvider.isExpanded(postNumber)) {
			holder.comment.setLinesLimit(commentMaxLines, commentAdditionalHeight);
		} else {
			holder.comment.setLinesLimit(0, 0);
		}
		holder.bottomBarExpand.setVisibility(View.GONE);
		holder.invalidateBottomBar();

		boolean viewsEnabled = demandSet.selection == UiManager.Selection.DISABLED;
		holder.thumbnail.setEnabled(viewsEnabled);
		holder.comment.setEnabled(viewsEnabled);
		holder.head.setEnabled(viewsEnabled);
		holder.bottomBarReplies.setEnabled(viewsEnabled);
		holder.bottomBarReplies.setClickable(viewsEnabled);
		holder.bottomBarExpand.setEnabled(viewsEnabled);
		holder.bottomBarExpand.setClickable(viewsEnabled);
		holder.bottomBarOpenThread.setEnabled(viewsEnabled);
		holder.bottomBarOpenThread.setClickable(viewsEnabled);
		holder.installBackground();
	}

	public void bindPostViewInvalidateComment(RecyclerView.ViewHolder viewHolder) {
		PostViewHolder holder = (PostViewHolder) viewHolder;
		holder.comment.invalidateAllSpans();
	}

	public RecyclerView.ViewHolder createPostHiddenView(ViewGroup parent,
			UiManager.ConfigurationSet configurationSet) {
		return new HiddenViewHolder(parent, configurationSet, false, false);
	}

	public void bindPostHiddenView(RecyclerView.ViewHolder viewHolder, PostItem postItem) {
		HiddenViewHolder holder = (HiddenViewHolder) viewHolder;
		holder.postItem = postItem;
		holder.index.setText(postItem.getOrdinalIndexString());
		holder.number.setText("#" + postItem.getPostNumber());
		String description = postItem.getHideReason();
		if (description == null) {
			description = postItem.getSubjectOrComment();
		}
		holder.comment.setText(description);
		holder.configurationSet.postStateProvider.setRead(postItem.getPostNumber());
	}

	private static int getPostBackgroundColor(UiManager uiManager, UiManager.ConfigurationSet configurationSet) {
		ColorScheme colorScheme = ThemeEngine.getColorScheme(uiManager.getContext());
		return configurationSet.isDialog ? colorScheme.dialogBackgroundColor : colorScheme.windowBackgroundColor;
	}

	@SuppressLint("InflateParams")
	private void handlePostViewAttachments(PostViewHolder holder) {
		Context context = uiManager.getContext();
		Chan chan = Chan.get(holder.configurationSet.chanName);
		PostItem postItem = holder.postItem;
		List<AttachmentItem> attachmentItems = postItem.getAttachmentItems();
		if (attachmentItems != null && !attachmentItems.isEmpty()) {
			int size = attachmentItems.size();
			if (size >= 2 && Preferences.isAllAttachments()) {
				holder.thumbnail.resetImage(null);
				holder.thumbnail.setVisibility(View.GONE);
				holder.attachmentInfo.setVisibility(View.GONE);
				ArrayList<AttachmentHolder> attachmentHolders = holder.attachmentHolders;
				if (attachmentHolders == null) {
					attachmentHolders = new ArrayList<>();
					holder.attachmentHolders = attachmentHolders;
				}
				int holders = attachmentHolders.size();
				if (holders < size) {
					int postBackgroundColor = getPostBackgroundColor(uiManager, holder.configurationSet);
					int thumbnailsScale = Preferences.getThumbnailsScale();
					int textScale = Preferences.getTextScale();
					for (int i = holders; i < size; i++) {
						View view = LayoutInflater.from(context).inflate(R.layout.list_item_post_attachment, null);
						AttachmentHolder attachmentHolder = new AttachmentHolder();
						attachmentHolder.container = view;
						attachmentHolder.thumbnail = view.findViewById(R.id.thumbnail);
						attachmentHolder.attachmentInfo = view.findViewById(R.id.attachment_info);
						attachmentHolder.thumbnail.setDrawTouching(true);
						attachmentHolder.thumbnail.applyRoundedCorners(postBackgroundColor);
						attachmentHolder.thumbnail.setOnClickListener(attachmentHolder.thumbnailClickListener);
						attachmentHolder.thumbnail.setOnLongClickListener(attachmentHolder.thumbnailLongClickListener);
						attachmentHolder.attachmentInfo.getLayoutParams().width = multipleAttachmentInfoWidth;
						ViewGroup.LayoutParams thumbnailLayoutParams = attachmentHolder.thumbnail.getLayoutParams();
						if (thumbnailsScale != 100) {
							thumbnailLayoutParams.width = thumbnailWidth * thumbnailsScale / 100;
							thumbnailLayoutParams.height = thumbnailLayoutParams.width;
						} else {
							thumbnailLayoutParams.width = thumbnailWidth;
							thumbnailLayoutParams.height = thumbnailWidth;
						}
						if (textScale != 100) {
							ViewUtils.applyScaleSize(attachmentHolder.attachmentInfo);
						}
						attachmentHolders.add(attachmentHolder);
						holder.attachments.addView(view);
					}
				}
				boolean sfwMode = Preferences.isSfwMode();
				for (int i = 0; i < size; i++) {
					AttachmentHolder attachmentHolder = attachmentHolders.get(i);
					AttachmentItem attachmentItem = attachmentItems.get(i);
					attachmentItem.configureAndLoad(attachmentHolder.thumbnail, chan, false, false);
					attachmentHolder.thumbnailClickListener.update(i, false, holder.configurationSet.isDialog
							? GalleryOverlay.NavigatePostMode.MANUALLY : GalleryOverlay.NavigatePostMode.ENABLED);
					attachmentHolder.thumbnailLongClickListener.update(attachmentItem);
					attachmentHolder.thumbnail.setSfwMode(sfwMode);
					attachmentHolder.attachmentInfo.setText(attachmentItem.getDescription(AttachmentItem.FormatMode
							.THREE_LINES));
					attachmentHolder.container.setVisibility(View.VISIBLE);
				}
				for (int i = size; i < holders; i++) {
					AttachmentHolder attachmentHolder = attachmentHolders.get(i);
					ImageLoader.getInstance().cancel(attachmentHolder.thumbnail);
					attachmentHolder.thumbnail.resetImage(null);
					attachmentHolder.container.setVisibility(View.GONE);
				}
				holder.attachments.setVisibility(View.VISIBLE);
				holder.attachmentViewCount = size;
			} else {
				AttachmentItem attachmentItem = attachmentItems.get(0);
				attachmentItem.configureAndLoad(holder.thumbnail, chan, size > 1, false);
				holder.thumbnailClickListener.update(0, true, holder.configurationSet.isDialog
						? GalleryOverlay.NavigatePostMode.MANUALLY : GalleryOverlay.NavigatePostMode.ENABLED);
				holder.thumbnailLongClickListener.update(attachmentItem);
				holder.thumbnail.setSfwMode(Preferences.isSfwMode());
				holder.thumbnail.setVisibility(View.VISIBLE);
				holder.attachmentInfo.setText(postItem.getAttachmentsDescription(context,
						AttachmentItem.FormatMode.LONG));
				holder.attachmentInfo.setVisibility(View.VISIBLE);
				holder.attachments.setVisibility(View.GONE);
				holder.attachmentViewCount = 1;
			}
		} else {
			ImageLoader.getInstance().cancel(holder.thumbnail);
			holder.thumbnail.resetImage(null);
			holder.thumbnail.setVisibility(View.GONE);
			holder.attachmentInfo.setVisibility(View.GONE);
			holder.attachments.setVisibility(View.GONE);
			holder.attachmentViewCount = 1;
		}
	}

	private void handlePostViewIcons(PostViewHolder holder) {
		Context context = uiManager.getContext();
		Chan chan = Chan.get(holder.configurationSet.chanName);
		PostItem postItem = holder.postItem;
		List<Post.Icon> icons = postItem.getIcons();
		if (!icons.isEmpty() && Preferences.isDisplayIcons()) {
			if (holder.badgeImages == null) {
				holder.badgeImages = new ArrayList<>();
			}
			int count = holder.badgeImages.size();
			int add = icons.size() - count;
			// Create more image views for icons
			if (add > 0) {
				View anchorView = count > 0 ? holder.badgeImages.get(count - 1) : holder.index;
				int anchorIndex = holder.head.indexOfChild(anchorView) + 1;
				float density = ResourceUtils.obtainDensity(context);
				int size = (int) (12f * density);
				for (int i = 0; i < add; i++) {
					ImageView imageView = new ImageView(context);
					holder.head.addView(imageView, anchorIndex + i, new ViewGroup.LayoutParams(size, size));
					ViewUtils.applyScaleSize(imageView);
					holder.badgeImages.add(imageView);
				}
			}
			for (int i = 0; i < holder.badgeImages.size(); i++) {
				ImageView imageView = holder.badgeImages.get(i);
				if (i < icons.size()) {
					imageView.setVisibility(View.VISIBLE);
					Uri uri = icons.get(i).uri;
					if (uri != null) {
						uri = uri.isRelative() ? chan.locator.convert(uri) : uri;
						ImageLoader.getInstance().loadImage(chan, uri, false, imageView);
					} else {
						ImageLoader.getInstance().cancel(imageView);
						imageView.setTag(null);
						imageView.setImageDrawable(null);
					}
				} else {
					ImageLoader.getInstance().cancel(imageView);
					imageView.setVisibility(View.GONE);
				}
			}
		} else if (holder.badgeImages != null) {
			for (ImageView imageView : holder.badgeImages) {
				imageView.setTag(null);
				imageView.setVisibility(View.GONE);
			}
		}
	}

	private CharSequence makeHighlightedText(Collection<String> highlightText, CharSequence text) {
		if (!highlightText.isEmpty() && text != null) {
			Locale locale = Locale.getDefault();
			SpannableString spannable = new SpannableString(text);
			String searchable = text.toString().toLowerCase(locale);
			ColorScheme colorScheme = ThemeEngine.getColorScheme(uiManager.getContext());
			for (String highlight : highlightText) {
				highlight = highlight.toLowerCase(locale);
				int textIndex = -1;
				while ((textIndex = searchable.indexOf(highlight, textIndex + 1)) >= 0) {
					spannable.setSpan(new BackgroundColorSpan(colorScheme.highlightTextColor),
							textIndex, textIndex + highlight.length(), SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE);
				}
				text = spannable;
			}
		}
		return text;
	}

	boolean handlePostForDoubleClick(final View view) {
		final PostViewHolder holder = ListViewUtils.getViewHolder(view, PostViewHolder.class);
		if (holder != null) {
			if (holder.comment.getVisibility() != View.VISIBLE || holder.comment.isSelectionMode()) {
				return false;
			}
			long t = SystemClock.elapsedRealtime();
			long timeout = holder.comment.getPreferredDoubleTapTimeout();
			if (t - holder.lastCommentClick > timeout) {
				holder.lastCommentClick = t;
			} else {
				final RecyclerView recyclerView = (RecyclerView) view.getParent();
				final int position = recyclerView.getChildAdapterPosition(view);
				holder.comment.startSelection();
				int padding = holder.comment.getSelectionPadding();
				if (padding > 0) {
					final int listHeight = recyclerView.getHeight() - recyclerView.getPaddingTop() -
							recyclerView.getPaddingBottom();
					recyclerView.post(() -> {
						int end = holder.comment.getSelectionEnd();
						if (end >= 0) {
							Layout layout = holder.comment.getLayout();
							int line = layout.getLineForOffset(end);
							int count = layout.getLineCount();
							if (count - line <= 4) {
								((LinearLayoutManager) recyclerView.getLayoutManager())
										.scrollToPositionWithOffset(position, listHeight - view.getHeight());
							}
						}
					});
				}
			}
			return true;
		} else {
			return false;
		}
	}

	private final View.OnClickListener repliesBlockClickListener = new View.OnClickListener() {
		@Override
		public void onClick(View v) {
			PostViewHolder holder = ListViewUtils.getViewHolder(v, PostViewHolder.class);
			PostItem postItem = holder.postItem;
			uiManager.dialog().displayReplies(holder.configurationSet, postItem);
		}
	};

	private final View.OnClickListener threadLinkBlockClickListener = new View.OnClickListener() {
		@Override
		public void onClick(View v) {
			PostViewHolder holder = ListViewUtils.getViewHolder(v, PostViewHolder.class);
			PostItem postItem = holder.postItem;
			PostNumber postNumber = postItem.isOriginalPost() ? null : postItem.getPostNumber();
			uiManager.navigator().navigatePosts(holder.configurationSet.chanName, postItem.getBoardName(),
					postItem.getThreadNumber(), postNumber, null);
		}
	};

	private final View.OnClickListener threadShowOriginalPostClickListener = new View.OnClickListener() {
		@Override
		public void onClick(View v) {
			ThreadViewHolder holder = ListViewUtils.getViewHolder(v, ThreadViewHolder.class);
			uiManager.dialog().displayThread(holder.getConfigurationSet(), holder.getPostItem());
		}
	};

	private final View.OnTouchListener headContentTouchListener = new View.OnTouchListener() {
		private static final int TYPE_NONE = 0;
		private static final int TYPE_BADGES = 1;
		private static final int TYPE_STATES = 2;

		private int type;
		private float startX, startY;

		@SuppressLint("ClickableViewAccessibility")
		@Override
		public boolean onTouch(View v, MotionEvent event) {
			switch (event.getAction()) {
				case MotionEvent.ACTION_DOWN: {
					type = TYPE_NONE;
					float x = event.getX();
					float y = event.getY();
					PostViewHolder holder = ListViewUtils.getViewHolder(v, PostViewHolder.class);
					LinebreakLayout head = holder.head;
					for (int i = 0; i < head.getChildCount(); i++) {
						View child = head.getChildAt(i);
						if (child.getVisibility() == View.VISIBLE) {
							int width = child.getWidth();
							int height = child.getHeight();
							int radius = (int) (Math.sqrt(Math.pow(width, 2) + Math.pow(height, 2)) / 2f);
							int centerX = child.getLeft() + child.getWidth() / 2;
							int centerY = child.getTop() + child.getHeight() / 2;
							int distance = (int) (Math.sqrt(Math.pow(centerX - x, 2) + Math.pow(centerY - y, 2)) / 2f);
							if (distance <= radius * 3 / 2) {
								startX = x;
								startY = y;
								// noinspection SuspiciousMethodCalls
								if (holder.badgeImages != null && holder.badgeImages.contains(child)) {
									type = TYPE_BADGES;
									return true;
								}
								// noinspection SuspiciousMethodCalls
								if (Arrays.asList(holder.stateImages).contains(child)) {
									type = TYPE_STATES;
									return true;
								}
							}
						}
					}
					break;
				}
				case MotionEvent.ACTION_UP: {
					if (type != TYPE_NONE) {
						Context context = uiManager.getContext();
						PostViewHolder holder = ListViewUtils.getViewHolder(v, PostViewHolder.class);
						int touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
						if (Math.abs(event.getX() - startX) <= touchSlop &&
								Math.abs(event.getY() - startY) <= touchSlop) {
							ArrayList<DialogUnit.IconData> icons = new ArrayList<>();
							String emailToCopy = null;
							switch (type) {
								case TYPE_BADGES: {
									Chan chan = Chan.get(holder.configurationSet.chanName);
									List<Post.Icon> postIcons = holder.postItem.getIcons();
									for (Post.Icon postIcon : postIcons) {
										Uri uri = postIcon.uri;
										if (uri != null) {
											uri = uri.isRelative() ? chan.locator.convert(uri) : uri;
										}
										icons.add(new DialogUnit.IconData(postIcon.title, uri));
									}
									break;
								}
								case TYPE_STATES: {
									for (int i = 0; i < PostState.POST_ITEM_STATES.size(); i++) {
										if (holder.stateImages[i].getVisibility() == View.VISIBLE) {
											PostState postState = PostState.POST_ITEM_STATES.get(i);
											String title = postState.titleProvider
													.get(uiManager.getContext(), holder.postItem);
											icons.add(new DialogUnit.IconData(title, postState.iconAttrResId));
										}
									}
									break;
								}
							}
							uiManager.dialog().showPostDescriptionDialog(icons,
									holder.configurationSet.chanName, emailToCopy);
						}
						return true;
					}
					break;
				}
			}
			return false;
		}
	};

	private enum PostState {
		USER_POST(R.attr.iconPostUserPost, R.string.my_post,
				data -> Preferences.isShowMyPosts() && data.configurationSet.postStateProvider
						.isUserPost(data.postItem.getPostNumber())),
		ORIGINAL_POSTER(R.attr.iconPostOriginalPoster, R.string.original_poster,
				data -> data.postItem.isOriginalPoster()),
		SAGE(R.attr.iconPostSage, R.string.doesnt_bring_up_thread,
				data -> data.postItem.isSage() || data.bumpLimitReached),
		EMAIL(R.attr.iconPostEmail,
				(context, postItem) -> {
					String email = postItem.getEmail();
					if (email != null && email.startsWith("mailto:")) {
						email = email.substring(7);
					}
					return email;
				},
				data -> !StringUtils.isEmpty(data.postItem.getEmail())),
		STICKY(R.attr.iconPostSticky, R.string.sticky_thread, data -> data.postItem.isSticky()),
		CLOSED(R.attr.iconPostClosed, R.string.thread_is_closed, data -> data.postItem.isClosed()),
		CYCLICAL(R.attr.iconPostCyclical, R.string.cyclical_thread, data -> data.postItem.isCyclical()),
		WARNED(R.attr.iconPostWarned, R.string.user_is_warned, data -> data.postItem.isPosterWarned()),
		BANNED(R.attr.iconPostBanned, R.string.user_is_banned, data -> data.postItem.isPosterBanned());

		public interface TitleProvider {
			String get(Context context, PostItem postItem);
		}

		public interface Predicate {
			class Data {
				public final PostItem postItem;
				public final UiManager.ConfigurationSet configurationSet;
				public final boolean bumpLimitReached;

				public Data(PostItem postItem, UiManager.ConfigurationSet configurationSet, boolean bumpLimitReached) {
					this.postItem = postItem;
					this.configurationSet = configurationSet;
					this.bumpLimitReached = bumpLimitReached;
				}
			}

			boolean apply(Data data);
		}

		public static final List<PostState> POST_ITEM_STATES = Arrays
				.asList(USER_POST, ORIGINAL_POSTER, SAGE, EMAIL, STICKY, CLOSED, CYCLICAL, WARNED, BANNED);

		public static final List<PostState> THREAD_ITEM_STATES = Arrays
				.asList(SAGE, STICKY, CLOSED, CYCLICAL);

		public final int iconAttrResId;
		public final TitleProvider titleProvider;
		public final Predicate predicate;

		PostState(int iconAttrResId, int titleResId, Predicate predicate) {
			this(iconAttrResId, (c, p) -> c.getString(titleResId), predicate);
		}

		PostState(int iconAttrResId, TitleProvider titleProvider, Predicate predicate) {
			this.iconAttrResId = iconAttrResId;
			this.titleProvider = titleProvider;
			this.predicate = predicate;
		}
	}

	private class AttachmentHolder {
		public AttachmentView thumbnail;
		public final UiManager.ThumbnailClickListener thumbnailClickListener;
		public final UiManager.ThumbnailLongClickListener thumbnailLongClickListener;

		public View container;
		public TextView attachmentInfo;

		public AttachmentHolder() {
			thumbnailClickListener = uiManager.interaction().createThumbnailClickListener();
			thumbnailLongClickListener = uiManager.interaction().createThumbnailLongClickListener();
		}
	}

	private static CardView createCardLayout(ViewGroup parent) {
		ThemeEngine.Theme theme = ThemeEngine.getTheme(parent.getContext());
		CardView cardView = new CardView(parent.getContext());
		cardView.setBackgroundColor(theme.card);
		FrameLayout content = new FrameLayout(cardView.getContext());
		ViewUtils.setSelectableItemBackground(content);
		cardView.addView(content, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		cardView.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
				ViewGroup.LayoutParams.WRAP_CONTENT));
		return cardView;
	}

	private static void fillStateImages(ViewGroup parent, int anchorIndex,
			ImageView[] images, List<PostState> states, float topDp, float startDp, float endDp) {
		float density = ResourceUtils.obtainDensity(parent);
		int size = (int) (12f * density + 0.5f);
		int top = (int) (topDp * density + 0.5f);
		int start = (int) (startDp * density + 0.5f);
		int end = (int) (endDp * density + 0.5f);
		boolean rtl = ViewCompat.getLayoutDirection(parent) == ViewCompat.LAYOUT_DIRECTION_RTL;
		int left = rtl ? end : start;
		int right = rtl ? start : end;
		int[] attrs = new int[states.size()];
		for (int i = 0; i < attrs.length; i++) {
			attrs[i] = states.get(i).iconAttrResId;
		}
		TypedArray typedArray = parent.getContext().obtainStyledAttributes(attrs);
		for (int i = 0; i < images.length; i++) {
			ImageView imageView = new ImageView(parent.getContext());
			imageView.setImageDrawable(typedArray.getDrawable(i));
			parent.addView(imageView, anchorIndex + i, new ViewGroup.LayoutParams(size, size));
			ViewGroup.LayoutParams layoutParams = imageView.getLayoutParams();
			if (layoutParams instanceof ViewGroup.MarginLayoutParams) {
				ViewGroup.MarginLayoutParams marginLayoutParams = (ViewGroup.MarginLayoutParams) layoutParams;
				marginLayoutParams.topMargin = top;
				marginLayoutParams.leftMargin = left;
				marginLayoutParams.rightMargin = right;
			}
			images[i] = imageView;
		}
		typedArray.recycle();
		if (C.API_LOLLIPOP && images.length > 0) {
			ColorStateList tint = ColorStateList.valueOf(ThemeEngine.getTheme(images[0].getContext()).meta);
			for (ImageView image : images) {
				image.setImageTintList(tint);
			}
		}
	}

	private static class ThreadViewHolder extends RecyclerView.ViewHolder implements UiManager.Holder {
		public enum ViewType {LIST, CARD, CELL}

		public final AttachmentView thumbnail;
		public final TextView subject;
		public final TextView comment;
		public final TextView description;
		public final ImageView[] stateImages;
		public final View threadContent;
		public final View showOriginalPost;

		public final UiManager.ConfigurationSet configurationSet;
		public final UiManager.ThumbnailClickListener thumbnailClickListener;
		public final UiManager.ThumbnailLongClickListener thumbnailLongClickListener;

		public PostItem postItem;

		public ThreadViewHolder(ViewGroup parent, UiManager.ConfigurationSet configurationSet,
				UiManager uiManager, ViewType viewType) {
			super(viewType != ViewType.LIST ? createCardLayout(parent)
					: LayoutInflater.from(parent.getContext()).inflate(R.layout.list_item_thread, parent, false));

			int thumbnailBackground;
			if (viewType != ViewType.LIST) {
				CardView cardView = (CardView) itemView;
				ViewGroup cardContent = (ViewGroup) cardView.getChildAt(0);
				LayoutInflater inflater = LayoutInflater.from(itemView.getContext());
				inflater.inflate(viewType == ViewType.CELL ? R.layout.list_item_thread_cell
						: R.layout.list_item_thread_card, cardContent);
				thumbnailBackground = cardView.getBackgroundColor();
			} else {
				ViewUtils.setSelectableItemBackground(itemView);
				thumbnailBackground = getPostBackgroundColor(uiManager, configurationSet);
			}

			thumbnail = itemView.findViewById(R.id.thumbnail);
			subject = itemView.findViewById(R.id.subject);
			comment = itemView.findViewById(R.id.comment);
			description = itemView.findViewById(R.id.thread_description);
			threadContent = itemView.findViewById(R.id.thread_content);
			ViewGroup showOriginalPost = itemView.findViewById(R.id.show_original_post);
			this.showOriginalPost = showOriginalPost;
			(viewType == ViewType.CELL ? description : showOriginalPost).setOnClickListener(uiManager.view()
					.threadShowOriginalPostClickListener);

			this.configurationSet = configurationSet;
			thumbnailClickListener = uiManager.interaction().createThumbnailClickListener();
			thumbnailLongClickListener = uiManager.interaction().createThumbnailLongClickListener();

			thumbnail.setDrawTouching(true);
			if (viewType == ViewType.CELL) {
				thumbnail.setFitSquare(true);
				thumbnail.applyRoundedCorners(thumbnailBackground);
				ViewUtils.applyScaleSize(comment, subject, description);
				stateImages = null;
			} else {
				stateImages = new ImageView[PostState.THREAD_ITEM_STATES.size()];
				fillStateImages(showOriginalPost, viewType == ViewType.CARD ? 1 : 0,
						stateImages, PostState.THREAD_ITEM_STATES, 0.5f,
						viewType == ViewType.CARD ? 8 : 0, viewType == ViewType.CARD ? 0 : 8);
				thumbnail.applyRoundedCorners(thumbnailBackground);
				ViewGroup.MarginLayoutParams thumbnailLayoutParams =
						(ViewGroup.MarginLayoutParams) thumbnail.getLayoutParams();
				ViewUtils.applyScaleSize(comment, subject, description);
				ViewUtils.applyScaleSize(stateImages);
				if (ResourceUtils.isTablet(itemView.getResources().getConfiguration())) {
					float density = ResourceUtils.obtainDensity(itemView);
					description.setGravity(Gravity.START);
					int thumbnailSize = (int) (72f * density);
					thumbnailLayoutParams.width = thumbnailSize;
					thumbnailLayoutParams.height = thumbnailSize;
					int descriptionPaddingLeft = thumbnailSize + thumbnailLayoutParams.leftMargin +
							thumbnailLayoutParams.rightMargin;
					description.setPadding(descriptionPaddingLeft, description.getPaddingTop(),
							description.getPaddingRight(), description.getPaddingBottom());
					comment.setMaxLines(8);
				} else {
					description.setGravity(viewType == ViewType.LIST ? Gravity.START : Gravity.END);
					comment.setMaxLines(6);
				}
				int thumbnailsScale = Preferences.getThumbnailsScale();
				thumbnailLayoutParams.width = thumbnailLayoutParams.width * thumbnailsScale / 100;
				thumbnailLayoutParams.height = thumbnailLayoutParams.height * thumbnailsScale / 100;
			}
		}

		@Override
		public PostItem getPostItem() {
			return postItem;
		}

		@Override
		public UiManager.ConfigurationSet getConfigurationSet() {
			return configurationSet;
		}

		@Override
		public GalleryItem.Set getGallerySet() {
			return postItem.getThreadGallerySet();
		}
	}

	private static class NewPostAnimation implements Runnable, ValueAnimator.AnimatorUpdateListener {
		private final PostLinearLayout layout;
		private final UiManager.PostStateProvider postStateProvider;
		private final PostNumber postNumber;
		private final ColorDrawable drawable;

		private ValueAnimator animator;
		private boolean applied = false;

		public NewPostAnimation(PostLinearLayout layout, UiManager.PostStateProvider postStateProvider,
				PostNumber postNumber, int color) {
			this.layout = layout;
			this.postStateProvider = postStateProvider;
			this.postNumber = postNumber;
			drawable = new ColorDrawable(color);
			layout.setSecondaryBackground(drawable);
			layout.postDelayed(this, 500);
		}

		@Override
		public void run() {
			int color = drawable.getColor();
			animator = ValueAnimator.ofObject(new ArgbEvaluator(), color, color & 0x00ffffff);
			animator.addUpdateListener(this);
			animator.setDuration(500);
			animator.start();
		}

		@Override
		public void onAnimationUpdate(ValueAnimator animation) {
			if (!applied) {
				applied = true;
				postStateProvider.setRead(postNumber);
			}
			drawable.setColor((int) animation.getAnimatedValue());
		}

		public void cancel() {
			layout.removeCallbacks(this);
			if (animator != null) {
				animator.cancel();
				animator = null;
			}
		}
	}

	private static class PostViewHolder extends RecyclerView.ViewHolder implements UiManager.Holder,
			CommentTextView.RecyclerKeeper.Holder, View.OnAttachStateChangeListener,
			CommentTextView.LimitListener, View.OnClickListener {
		public final PostLinearLayout layout;
		public final LinebreakLayout head;
		public final TextView number;
		public final TextView name;
		public final TextView index;
		public final TextView date;
		public final ViewGroup attachments;
		public final AttachmentView thumbnail;
		public final TextView attachmentInfo;
		public final CommentTextView comment;
		public final View textSelectionPadding;
		public final View textBarPadding;
		public final View bottomBar;
		public final TextView bottomBarReplies;
		public final TextView bottomBarExpand;
		public final TextView bottomBarOpenThread;

		public ArrayList<AttachmentHolder> attachmentHolders;
		public int attachmentViewCount = 1;
		public ArrayList<ImageView> badgeImages;
		public final ImageView[] stateImages = new ImageView[PostState.POST_ITEM_STATES.size()];
		public final int highlightBackgroundColor;

		public final UiManager.ConfigurationSet configurationSet;
		public final UiManager.ThumbnailClickListener thumbnailClickListener;
		public final UiManager.ThumbnailLongClickListener thumbnailLongClickListener;

		public PostItem postItem;
		public UiManager.Selection selection;
		public Animator expandAnimator;
		public NewPostAnimation newPostAnimation;
		public long lastCommentClick;

		public PostViewHolder(ViewGroup parent, UiManager.ConfigurationSet configurationSet,
				UiManager uiManager, int thumbnailWidth) {
			super(LayoutInflater.from(parent.getContext()).inflate(R.layout.list_item_post, parent, false));

			layout = (PostLinearLayout) itemView;
			layout.addOnAttachStateChangeListener(this);
			ViewUtils.setSelectableItemBackground(layout);
			head = itemView.findViewById(R.id.head);
			number = itemView.findViewById(R.id.number);
			name = itemView.findViewById(R.id.name);
			index = itemView.findViewById(R.id.index);
			date = itemView.findViewById(R.id.date);
			fillStateImages(head, head.indexOfChild(number) + 1, stateImages, PostState.POST_ITEM_STATES, 0, 0, 0);
			attachments = itemView.findViewById(R.id.attachments);
			thumbnail = itemView.findViewById(R.id.thumbnail);
			attachmentInfo = itemView.findViewById(R.id.attachment_info);
			comment = itemView.findViewById(R.id.comment);
			textSelectionPadding = itemView.findViewById(R.id.text_selection_padding);
			textBarPadding = itemView.findViewById(R.id.text_bar_padding);
			bottomBar = itemView.findViewById(R.id.bottom_bar);
			bottomBarReplies = itemView.findViewById(R.id.bottom_bar_replies);
			bottomBarExpand = itemView.findViewById(R.id.bottom_bar_expand);
			bottomBarOpenThread = itemView.findViewById(R.id.bottom_bar_open_thread);
			highlightBackgroundColor = ThemeEngine.getColorScheme(itemView.getContext()).highlightBackgroundColor;

			this.configurationSet = configurationSet;
			thumbnailClickListener = uiManager.interaction().createThumbnailClickListener();
			thumbnailLongClickListener = uiManager.interaction().createThumbnailLongClickListener();

			head.setOnTouchListener(uiManager.view().headContentTouchListener);
			comment.setLimitListener(this);
			comment.setSpanStateListener(uiManager.view().spanStateListener);
			comment.setPrepareToCopyListener(uiManager.view().prepareToCopyListener);
			comment.setExtraButtons(uiManager.view().extraButtons);
			thumbnail.setOnClickListener(thumbnailClickListener);
			thumbnail.setOnLongClickListener(thumbnailLongClickListener);
			bottomBarReplies.setOnClickListener(uiManager.view().repliesBlockClickListener);
			bottomBarExpand.setOnClickListener(this);
			bottomBarOpenThread.setOnClickListener(uiManager.view().threadLinkBlockClickListener);

			index.setTypeface(GraphicsUtils.TYPEFACE_MEDIUM);
			int textScale = Preferences.getTextScale();
			if (textScale != 100) {
				ViewUtils.applyScaleSize(number, name, index, date, comment, attachmentInfo,
						bottomBarReplies, bottomBarExpand, bottomBarOpenThread);
				ViewUtils.applyScaleSize(stateImages);
				head.setHorizontalSpacing(head.getHorizontalSpacing() * textScale / 100);
			}

			thumbnail.setDrawTouching(true);
			thumbnail.applyRoundedCorners(getPostBackgroundColor(uiManager, configurationSet));
			ViewGroup.LayoutParams thumbnailLayoutParams = thumbnail.getLayoutParams();
			int thumbnailsScale = Preferences.getThumbnailsScale();
			if (thumbnailsScale != 100) {
				thumbnailLayoutParams.width = thumbnailWidth * thumbnailsScale / 100;
				thumbnailLayoutParams.height = thumbnailLayoutParams.width;
			} else {
				thumbnailLayoutParams.width = thumbnailWidth;
			}
		}

		public void installBackground() {
			if (ViewCompat.isAttachedToWindow(itemView)) {
				installBackgroundUnchecked();
			}
		}

		private void installBackgroundUnchecked() {
			if (newPostAnimation != null) {
				newPostAnimation.cancel();
				newPostAnimation = null;
			}
			if (selection == UiManager.Selection.DISABLED &&
					!configurationSet.postStateProvider.isRead(postItem.getPostNumber())) {
				switch (Preferences.getHighlightUnreadMode()) {
					case AUTOMATICALLY: {
						newPostAnimation = new NewPostAnimation(layout,
								configurationSet.postStateProvider, postItem.getPostNumber(),
								highlightBackgroundColor);
						break;
					}
					case MANUALLY: {
						layout.setSecondaryBackgroundColor(highlightBackgroundColor);
						break;
					}
					case NEVER: {
						layout.setSecondaryBackground(null);
						break;
					}
					default: {
						throw new IllegalStateException();
					}
				}
			} else if (selection == UiManager.Selection.SELECTED) {
				layout.setSecondaryBackgroundColor(highlightBackgroundColor);
			} else {
				layout.setSecondaryBackground(null);
			}
		}

		public void resetAnimations() {
			if (expandAnimator != null) {
				expandAnimator.cancel();
				expandAnimator = null;
				comment.getLayoutParams().height = ViewGroup.LayoutParams.WRAP_CONTENT;
			}
			if (newPostAnimation != null) {
				newPostAnimation.cancel();
				newPostAnimation = null;
			}
		}

		public void invalidateBottomBar() {
			boolean repliesVisible = bottomBarReplies.getVisibility() == View.VISIBLE;
			boolean expandVisible = bottomBarExpand.getVisibility() == View.VISIBLE;
			boolean openThreadVisible = bottomBarOpenThread.getVisibility() == View.VISIBLE;
			boolean needBar = repliesVisible || expandVisible || openThreadVisible;
			bottomBarReplies.getLayoutParams().width = repliesVisible && !expandVisible && !openThreadVisible ?
					ViewGroup.LayoutParams.MATCH_PARENT : ViewGroup.LayoutParams.WRAP_CONTENT;
			bottomBarExpand.getLayoutParams().width = expandVisible && !openThreadVisible ?
					ViewGroup.LayoutParams.MATCH_PARENT : ViewGroup.LayoutParams.WRAP_CONTENT;
			bottomBar.setVisibility(needBar ? View.VISIBLE : View.GONE);
			boolean hasText = comment.getVisibility() == View.VISIBLE;
			float density = ResourceUtils.obtainDensity(textBarPadding);
			textBarPadding.getLayoutParams().height = (int) ((needBar ? 0f : hasText ? 10f : 6f) * density);
		}

		@Override
		public void onViewAttachedToWindow(View v) {
			installBackgroundUnchecked();
		}

		@Override
		public void onViewDetachedFromWindow(View v) {
			resetAnimations();
		}

		@Override
		public void onApplyLimit(boolean limited) {
			if (limited != (bottomBarExpand.getVisibility() == View.VISIBLE)) {
				bottomBarExpand.setVisibility(limited ? View.VISIBLE : View.GONE);
				invalidateBottomBar();
			}
		}

		@Override
		public void onClick(View v) {
			if (v == bottomBarExpand && postItem != null &&
					!configurationSet.postStateProvider.isExpanded(postItem.getPostNumber())) {
				configurationSet.postStateProvider.setExpanded(postItem.getPostNumber());
				comment.setLinesLimit(0, 0);
				bottomBarExpand.setVisibility(View.GONE);
				int bottomBarHeight = bottomBar.getHeight();
				invalidateBottomBar();
				if (expandAnimator != null) {
					expandAnimator.cancel();
				}
				int fromHeight = comment.getHeight();
				AnimationUtils.measureDynamicHeight(comment);
				int toHeight = comment.getMeasuredHeight();
				if (bottomBarHeight > 0 && bottomBar.getVisibility() == View.GONE) {
					// When button bar becomes hidden, height of the view becomes smaller, so it can cause
					// a short list jump; Solution - start the animation from fromHeight + bottomBarHeight
					fromHeight += bottomBarHeight;
				}
				if (toHeight > fromHeight) {
					float density = ResourceUtils.obtainDensity(comment);
					float value = (toHeight - fromHeight) / density / 400;
					if (value > 1f) {
						value = 1f;
					} else if (value < 0.2f) {
						value = 0.2f;
					}
					Animator animator = AnimationUtils.ofHeight(comment, fromHeight,
							ViewGroup.LayoutParams.WRAP_CONTENT, false);
					this.expandAnimator = animator;
					animator.setDuration((int) (200 * value));
					animator.start();
				}
			}
		}

		@Override
		public PostItem getPostItem() {
			return postItem;
		}

		@Override
		public UiManager.ConfigurationSet getConfigurationSet() {
			return configurationSet;
		}

		@Override
		public GalleryItem.Set getGallerySet() {
			return configurationSet.gallerySet;
		}

		@Override
		public CommentTextView getCommentTextView() {
			return comment;
		}
	}

	private static class HiddenViewHolder extends RecyclerView.ViewHolder implements UiManager.Holder {
		public final TextView index;
		public final TextView number;
		public final TextView comment;

		public final UiManager.ConfigurationSet configurationSet;

		public PostItem postItem;

		private static View createBaseView(ViewGroup parent, boolean card) {
			if (card) {
				CardView cardView = createCardLayout(parent);
				ViewGroup cardContent = (ViewGroup) cardView.getChildAt(0);
				LayoutInflater.from(cardView.getContext()).inflate(R.layout.list_item_hidden, cardContent);
				return cardView;
			} else {
				return LayoutInflater.from(parent.getContext()).inflate(R.layout.list_item_hidden, parent, false);
			}
		}

		public HiddenViewHolder(ViewGroup parent, UiManager.ConfigurationSet configurationSet,
				boolean card, boolean thread) {
			super(createBaseView(parent, card));

			index = itemView.findViewById(R.id.index);
			number = itemView.findViewById(R.id.number);
			comment = itemView.findViewById(R.id.comment);
			itemView.findViewById(R.id.head).setAlpha(ALPHA_HIDDEN_POST);
			ViewUtils.applyScaleSize(index, number, comment);
			ViewUtils.applyScaleMarginLR(index, number, comment);
			if (thread) {
				index.setVisibility(View.GONE);
				number.setVisibility(View.GONE);
			}
			if (!card) {
				ViewUtils.setSelectableItemBackground(itemView);
			}

			this.configurationSet = configurationSet;
		}

		@Override
		public PostItem getPostItem() {
			return postItem;
		}

		@Override
		public UiManager.ConfigurationSet getConfigurationSet() {
			return configurationSet;
		}

		@Override
		public GalleryItem.Set getGallerySet() {
			throw new UnsupportedOperationException();
		}
	}
}
