package com.serenegiant.widget;
/*
 * ViewSplitterTest
 *
 * Copyright (c) 2020 saki t_saki@serenegiant.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
*/

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.PaintDrawable;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;

import com.serenegiant.viewsplittertest.R;

import java.util.HashMap;
import java.util.Map;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.LinearLayoutCompat;

/**
 * ユーザー操作によって子Viewのリサイズ可能なLinearLayout
 */
public class SplitLinearLayout extends LinearLayout {
	private static final boolean DEBUG = false;	// set false on production
	private static final String TAG = SplitLinearLayout.class.getSimpleName();

	/**
	 * デフォルトのスプリッターサイズ[dp]
	 */
	private static final int DEFAULT_SPLITTER_SIZE_DP = 2;
	/**
	 * 実際のスプリッターのサイズよりもタッチ領域を拡大するための定数[dp]
	 */
	private static final int DEFAULT_TOUCH_EXTRA_SIZE_DP = 12;
	/**
	 * Divider(スプリッター用のDrawable)がセットされていないときにデフォルトで使うDrawableの色
	 */
	private static final int DEFAULT_SPLITTER_COLOR = 0x7fff0000;

	/**
	 * スプリッター情報を保持するためのヘルパークラス
	 */
	private class Splitter {
		/**
		 * 関連するView(スプリッターの左側/上側)のViewGroup内でのインデックス
		 */
		private int index;
		/**
		 * スプリッター外形矩形
		 */
		private Rect bounds = new Rect();
		/**
		 * スプリッターのタッチ領域
		 */
		private Rect touchBounds = new Rect();
		/**
		 * スプリッターをドラッグしているときの外形矩形
		 */
		private Rect draggingBounds = new Rect();

		/**
		 * スプリッター外形矩形をセット
		 * タッチ領域もセットされる
		 * @param left
		 * @param top
		 * @param right
		 * @param bottom
		 */
		private void setBounds(final int left, final int top, final int right, final int bottom) {
			bounds.set(left, top, right, bottom);
			touchBounds.set(left, top, right, bottom);
			if (getOrientation() == VERTICAL) {
				touchBounds.inset(0, -mTouchExtraSizePx);
			} else {
				touchBounds.inset(-mTouchExtraSizePx, 0);
			}
			if (DEBUG) Log.v(TAG, "setBounds:" + bounds + ",touch=" + touchBounds);
		}

		/**
		 * スプリッターのドラッグを開始
		 */
		private void startDragging() {
			if (DEBUG) Log.v(TAG, "startDragging:" + bounds);
			draggingBounds.set(bounds);
		}

		/**
		 * スプリッターのドラッグを終了
		 */
		private void finishDragging() {
			if (DEBUG) Log.v(TAG, "finishDragging:" + draggingBounds);
			bounds.set(draggingBounds);
		}

		/**
		 * スプリッターをタッチしたかどうかを取得
		 * @param x
		 * @param y
		 * @return
		 */
		private boolean touchContains(final int x, final int y) {
			return touchBounds.contains(x, y);
		}
	}

//--------------------------------------------------------------------------------
	/**
	 * スプリッター
	 */
	private final Map<View, Splitter> mSplitters = new HashMap<>();
	/**
	 * スプリッターをユーザー操作で移動可能かどうか
	 */
	private boolean mEnableSplitter;
	/**
	 * ユーザーが操作中のSplitter
	 */
	@Nullable
	private Splitter activeSplitter;
	/**
	 * スプリッターサイズの/12
	 */
	private int mHalfSplitterSize;
	/**
	 * タッチ用にスプリッターのサイズを拡大する[ピクセル]
	 */
	private int mTouchExtraSizePx;
	/**
	 * onMeasureを一番最初に実行したかどうかを保持
	 */
	private boolean  mIsFirstTime = true;

//--------------------------------------------------------------------------------
	/**
	 * コンストラクタ
	 * @param context
	 */
	public SplitLinearLayout(final Context context) {
		this(context, null, 0);
	}

	/**
	 * コンストラクタ
	 * @param context
	 * @param attrs
	 */
	public SplitLinearLayout(final Context context, @Nullable final AttributeSet attrs) {
		this(context, attrs, 0);
	}

	/**
	 * コンストラクタ
	 * @param context
	 * @param attrs
	 * @param defStyleAttr
	 */
	public SplitLinearLayout(final Context context, @Nullable final AttributeSet attrs, final int defStyleAttr) {
		super(context, attrs, defStyleAttr);
		if (DEBUG) Log.v(TAG, "コンストラクタ:");
		final DisplayMetrics metrics = getResources().getDisplayMetrics();
		TypedArray a = context.getTheme().obtainStyledAttributes(
			attrs, R.styleable.SplitLinearLayout, defStyleAttr, 0);
		try {
			// スプリッターのサイズ[ピクセル]
			int splitterSizePx = a.getDimensionPixelSize(R.styleable.SplitLinearLayout_splitterSize,
				(int) (DEFAULT_SPLITTER_SIZE_DP * metrics.density));
			if (splitterSizePx < 2) {
				splitterSizePx = 2;
			}
			mHalfSplitterSize = splitterSizePx / 2;
			// スプリッターのタッチ領域の拡大値
			mTouchExtraSizePx = a.getDimensionPixelSize(R.styleable.SplitLinearLayout_extraTouchSize,
				(int)(DEFAULT_TOUCH_EXTRA_SIZE_DP * metrics.density));
			if (mTouchExtraSizePx < 0) {
				mTouchExtraSizePx = 0;
			}
			// スプリッターをユーザーが操作できるようにするかどうか
			mEnableSplitter = a.getBoolean(R.styleable.SplitLinearLayout_splitterEnable, true);
		} finally {
			a.recycle();
		}
		if (getDividerDrawable() == null) {
			setDividerDrawable(new PaintDrawable(DEFAULT_SPLITTER_COLOR));
		}
		// dispatchDrawで自前描画するのでLinearLayout自体には描画させない
		super.setShowDividers(0);
		setDescendantFocusability(FOCUS_AFTER_DESCENDANTS);
		setFocusable(true);
		setFocusableInTouchMode(false);
	}

//--------------------------------------------------------------------------------
	/**
	 * スプリッター移動中の描画をするためLinearLayout自体によるDivider描画は使用できないので
	 * 常時UnsupportedOperationExceptionを投げる
	 * @param showDividers
	 * @throws UnsupportedOperationException
	 */
	@Override
	public void setShowDividers(@LinearLayoutCompat.DividerMode int showDividers)
		throws UnsupportedOperationException {

		throw new UnsupportedOperationException();
	}

	@Nullable
	@Override
	protected Parcelable onSaveInstanceState() {
		return super.onSaveInstanceState();
		// FIXME 未実装
	}

	@Override
	protected void onRestoreInstanceState(final Parcelable state) {
		// FIXME 未実装
		super.onRestoreInstanceState(state);
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		super.onMeasure(widthMeasureSpec, heightMeasureSpec);
		if (mIsFirstTime) {
			// 初回のレイアウト実行時はレイアウトファイルに定義されたとおりにViewの大きさを決めるために
			// 最初にonLayoutが呼ばれるまでは子Viewのサイズ調整を行わない
			return;
		}
		final int measuredWidth = getMeasuredWidth();
		final int measuredHeight = getMeasuredHeight();
		if (DEBUG) Log.v(TAG, String.format("onMeasure:(%dx%d))", measuredWidth, measuredHeight));
		if ((measuredWidth > 0) && (measuredHeight > 0)) {
			// スプリッターの位置に合わせて子Viewの位置を調整する
			final int childCount = getChildCount();
			int prev = 0;
			for (int i = 0; i < childCount; i++) {
				final View child = getChildAt(i);
				final Splitter splitter = getSplitter(child);
				int measSpecWidth;
				int measSpecHeight;
				if (splitter != null) {
					if (getOrientation() == VERTICAL) {
						if (DEBUG) Log.v(TAG, String.format("onMeasure:%d(%dx%d)", i, measuredWidth, splitter.bounds.centerY() - prev));
						measSpecWidth = MeasureSpec.makeMeasureSpec(measuredWidth, MeasureSpec.EXACTLY);
						measSpecHeight = MeasureSpec.makeMeasureSpec(splitter.bounds.centerY() - prev, MeasureSpec.EXACTLY);
						prev = splitter.bounds.centerY();
					} else {
						if (DEBUG) Log.v(TAG, String.format("onMeasure:%d(%dx%d)", i, splitter.bounds.centerX() - prev, measuredHeight));
						measSpecWidth = MeasureSpec.makeMeasureSpec(splitter.bounds.centerX() - prev, MeasureSpec.EXACTLY);
						measSpecHeight = MeasureSpec.makeMeasureSpec(measuredHeight , MeasureSpec.EXACTLY);
						prev = splitter.bounds.centerX();
					}
				} else {
					if (getOrientation() == VERTICAL) {
						if (DEBUG) Log.v(TAG, String.format("onMeasure:%d(%dx%d)", i, measuredWidth, measuredHeight - prev));
						measSpecWidth = MeasureSpec.makeMeasureSpec(measuredWidth, MeasureSpec.EXACTLY);
						measSpecHeight = MeasureSpec.makeMeasureSpec(measuredHeight - prev, MeasureSpec.EXACTLY);
						prev = measuredHeight;
					} else {
						if (DEBUG) Log.v(TAG, String.format("onMeasure:%d(%dx%d)", i, measuredWidth - prev, measuredHeight));
						measSpecWidth = MeasureSpec.makeMeasureSpec(measuredWidth - prev, MeasureSpec.EXACTLY);
						measSpecHeight = MeasureSpec.makeMeasureSpec(measuredHeight , MeasureSpec.EXACTLY);
						prev = measuredWidth;
					}
				}
				child.measure(measSpecWidth, measSpecHeight);
			}
		}
	}

	@Override
	protected void onLayout(final boolean changed,
		final int left, final int top, final int right, final int bottom) {

		super.onLayout(changed, left, top, right, bottom);
		if (DEBUG) Log.v(TAG,
			String.format("onLayout:(%d,%d)-(%d,%d)", left, top, right, bottom));
		// レイアウトしたサイズに合わせてスプリッターの情報を更新する
		updateSplitter();
		mIsFirstTime = false;
	}

	@SuppressLint("ClickableViewAccessibility")
	@Override
	public boolean onTouchEvent(final MotionEvent event) {
		if (mEnableSplitter) {
			final int action = event.getActionMasked();
			switch (action) {
			case MotionEvent.ACTION_DOWN:
				handleActionDown(event);
				break;
			case MotionEvent.ACTION_MOVE:
				handleActionMove(event);
				break;
			case MotionEvent.ACTION_CANCEL:
				break;
			case MotionEvent.ACTION_UP:
				handleActionUpn(event);
				break;
			}
			// FIXME デフォルトの動作は常に実行したほうがいいのかも
			return true;
		}
		return super.onTouchEvent(event);
	}

	/**
	 * LinearLayout自体でDividerを描画するとユーザー操作でスプリッターを移動させるときに
	 * スプリッター位置に移動してくれないのでdispatchDrawを上書きして自前で描画する
	 * @param canvas
	 */
	@Override
	protected void dispatchDraw(final Canvas canvas) {
		super.dispatchDraw(canvas);
		final Drawable drawable = getDividerDrawable();
		if (drawable != null) {
			for (@NonNull final Splitter splitter: mSplitters.values()) {
				if (DEBUG) {
					// デバッグフラグtrueのとこはタッチ領域を淡色で塗る
					final int alpha;
					if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
						alpha = drawable.getAlpha();
					} else {
						alpha = 255;
					}
					drawable.setBounds(splitter.touchBounds);
					drawable.setAlpha(50);
					drawable.draw(canvas);
					drawable.setAlpha(alpha);
				}
				if (splitter == activeSplitter) {
					// 移動中のスプリッター
					drawable.setBounds(activeSplitter.draggingBounds);
				} else {
					// 通常のスプリッター
					drawable.setBounds(splitter.bounds);
				}
				drawable.setState(getDrawableState());
				drawable.draw(canvas);
			}
		}
	}

//--------------------------------------------------------------------------------
	/**
	 * スプリッターをユーザー操作で移動可能にするかどうかを設定
	 * @param enable
	 */
	public void setSplitterEnable(final boolean enable) {
		mEnableSplitter = enable;
	}

	/**
	 * スプリッターをユーザー操作で移動可能かどうか
	 * @return
	 */
	public boolean getSplitterEnabled() {
		return mEnableSplitter;
	}

	/**
	 * タッチ領域の拡大幅[ピクセル]をセット
	 * @param extraSizePx
	 */
	public void setExtraTouchSize(@IntRange(from=0) final int extraSizePx) {
		if (mTouchExtraSizePx != extraSizePx) {
 			mTouchExtraSizePx = extraSizePx;
			updateSplitter();
		}
	}

	/**
	 * スプリッターのサイズをセット
	 * @param splitterSizePx
	 */
	public void setSplitterSize(@IntRange(from=2) final int splitterSizePx) {
		if (splitterSizePx < 2) {
			throw new IllegalArgumentException("Splitter size should be greater than 2.");
		}
		if (mHalfSplitterSize != splitterSizePx / 2) {
			mHalfSplitterSize = splitterSizePx / 2;
			postInvalidate();
		}
	}

	/**
	 * スプリッターのサイズを取得
	 * @return
	 */
	public int getSplitterSize() {
		return mHalfSplitterSize * 2;
	}

//--------------------------------------------------------------------------------
	private int lastTouchX, lastTouchY;
	/**
	 * スプリッターを移動中かどうか
	 */
	private boolean isSplitterMoving;

	/**
	 * スプリッターの最小位置を取得
	 * スプリッターに対応したViewの左端/上端 + 子Viewの最小幅/最小高さ
	 * @return
	 */
	private int getMinSplitterPos(@NonNull  final Splitter splitter) {
		final View view = getChildAt(splitter.index);
		if (getOrientation() == VERTICAL) {
			if (DEBUG) Log.v(TAG, "getMinSplitterPos:" + (view.getTop() + view.getMinimumHeight()));
			return view.getTop() + view.getMinimumHeight();
		} else {
			if (DEBUG) Log.v(TAG, "getMinSplitterPos:" + (view.getLeft() + view.getMinimumWidth()));
			return view.getLeft() + view.getMinimumWidth();
		}
	}

	/**
	 * スプリッターの最大位置
	 * スプリッターに対応したViewの1つ後ろ/１つ下のViewの右端/下端 - 子Viewの最小幅/最小高さ
	 * @return
	 */
	private int getMaxSplitterPos(@NonNull  final Splitter splitter) {
		final View view = getChildAt(splitter.index + 1);
		if (getOrientation() == VERTICAL) {
			if (DEBUG) Log.v(TAG, "getMaxSplitterPos:" + (view.getBottom() - view.getMinimumHeight()));
			return view.getBottom() - view.getMinimumHeight();
		} else {
			if (DEBUG) Log.v(TAG, "getMaxSplitterPos:" + (view.getRight() - view.getMinimumWidth()));
			return view.getRight() - view.getMinimumWidth();
		}
	}

	/**
	 * タッチしたときの処理の実態
	 * @param event
	 */
	private void handleActionDown(@NonNull final MotionEvent event) {
		final int x = (int)event.getX();
		final int y = (int)event.getY();
		if (DEBUG) Log.v(TAG, String.format("handleActionDown:(%d,%d)", x, y));
		Splitter found = null;
		int delta = Integer.MAX_VALUE;
		// 複数のスプリッターのタッチ領域が重なっているときのために
		// タッチした位置に一番近いタッチ可能なスプリッターを選択する
		for (final Splitter splitter: mSplitters.values()) {
			if (splitter.touchContains(x, y)) {
				if (DEBUG) Log.v(TAG, "handleActionDown:splitter found.");
				int d;
				if (getOrientation() == VERTICAL) {
					d = Math.abs(splitter.bounds.centerY() - y);
				} else {
					d = Math.abs(splitter.bounds.centerX() - x);
				}
				if ((found == null) || (d < delta)) {
					found = splitter;
					delta = d;
				}
			}
		}
		if (found != null) {
			performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
			found.startDragging();
			lastTouchX = x;
			lastTouchY = y;
			activeSplitter = found;
			invalidate(found.draggingBounds);
		}
	}

	/**
	 * ドラッグしたときの処理の実態
	 * @param event
	 */
	private void handleActionMove(@NonNull final MotionEvent event) {
		if (activeSplitter != null) {
			if (DEBUG) Log.v(TAG, "handleActionMove:");
			final int x = (int)event.getX();
			final int y = (int)event.getY();
			if (!isSplitterMoving) {
          		if (activeSplitter.touchContains(x, y)) {
          			if (DEBUG) Log.v(TAG, "handleActionMove:移動していなければ何もしない");
					return;
				} else {
					isSplitterMoving = true;
				}
			}
			// ローカルコピー
			final Rect draggingBounds = activeSplitter.draggingBounds;
			boolean moved = true;
			if (getOrientation() == VERTICAL) {
				draggingBounds.offset(0, y - lastTouchY);
				if (draggingBounds.centerY() < getMinSplitterPos(activeSplitter)) {
					moved = false;
					draggingBounds.offset(0, getMinSplitterPos(activeSplitter) - draggingBounds.centerY());
				}
				if (draggingBounds.centerY() > getMaxSplitterPos(activeSplitter)) {
					moved = false;
					draggingBounds.offset(0, getMaxSplitterPos(activeSplitter) - draggingBounds.centerY());
				}
			} else {
				draggingBounds.offset(x - lastTouchX, 0);
				if (draggingBounds.centerX() < getMinSplitterPos(activeSplitter)) {
					moved = false;
					draggingBounds.offset(getMinSplitterPos(activeSplitter) - draggingBounds.centerX(), 0);
				}
				if (draggingBounds.centerX() > getMaxSplitterPos(activeSplitter)) {
					moved = false;
					draggingBounds.offset(getMaxSplitterPos(activeSplitter) - draggingBounds.centerX(), 0);
				}
			}
			if (moved) {
				lastTouchX = x;
				lastTouchY = y;
				postInvalidate();
			}
		}
	}

	/**
	 * タッチ終了したときの処理の実態
	 * @param event
	 */
	private void handleActionUpn(@NonNull final MotionEvent event) {
		if (activeSplitter != null) {
			if (DEBUG) Log.v(TAG, "handleActionUpn:");
			final int x = (int)event.getX();
			final int y = (int)event.getY();
			isSplitterMoving = false;
			activeSplitter.finishDragging();
			activeSplitter = null;
			requestLayout();
			// サイズ変更通知をする?
		}
	}

	/**
	 * 子Viewの大きさに合わせてスプリッターの位置をセットする
	 */
	private void updateSplitter() {
		final int n = getChildCount() - 1;
		if (DEBUG) Log.v(TAG, "updateSplitter:" + (n + 1));
		final Map<View, Splitter> exist = new HashMap<>();
		for (int i = 0; i < n; i++) {
			final View child = getChildAt(i);
			Splitter splitter = getSplitter(child);
			if (splitter == null) {
				splitter = new Splitter();
				mSplitters.put(child, splitter);
			}
			splitter.index = i;
			exist.put(child, splitter);
			if (getOrientation() == VERTICAL) {
				splitter.setBounds(
					child.getLeft(), child.getBottom() - mHalfSplitterSize,
					child.getRight(), child.getBottom() + mHalfSplitterSize);
			} else {
				splitter.setBounds(
					child.getRight() - mHalfSplitterSize, child.getTop(),
					child.getRight() + mHalfSplitterSize, child.getBottom());
			}

		}
		mSplitters.clear();
		mSplitters.putAll(exist);
	}

	/**
	 * 指定した子Viewに対応するスプリッターを取得する
	 * スプリッターは子Viewの右端/下端に位置するので一番右/下のViewにはスプリッターは存在しない
	 * @param child
	 * @return
	 */
	@Nullable
	private Splitter getSplitter(@NonNull final View child) {
		return mSplitters.containsKey(child) ? mSplitters.get(child) : null;
	}

}
