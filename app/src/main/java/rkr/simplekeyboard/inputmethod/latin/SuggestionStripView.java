/*
 * Copyright (C) 2025 Simple Keyboard
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package rkr.simplekeyboard.inputmethod.latin;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import rkr.simplekeyboard.inputmethod.R;
import rkr.simplekeyboard.inputmethod.latin.prediction.Suggestion;

/**
 * Manages the suggestion strip UI and displays word suggestions.
 */
public class SuggestionStripView {
    private static final String TAG = "SuggestionStripView";
    private static final int MAX_VISIBLE_SUGGESTIONS = 5;

    private final Context mContext;
    private final LinearLayout mSuggestionChipsContainer;
    private final List<TextView> mSuggestionChips;
    private SuggestionClickListener mListener;

    /**
     * Callback interface for suggestion clicks.
     */
    public interface SuggestionClickListener {
        void onSuggestionClicked(Suggestion suggestion);
    }

    public SuggestionStripView(Context context, View rootView) {
        this.mContext = context;
        this.mSuggestionChipsContainer = rootView.findViewById(R.id.suggestion_chips_container);
        this.mSuggestionChips = new ArrayList<>();

        if (mSuggestionChipsContainer == null) {
            Log.e(TAG, "Suggestion chips container not found!");
        }
    }

    /**
     * Set the click listener for suggestions.
     *
     * @param listener The click listener
     */
    public void setListener(SuggestionClickListener listener) {
        this.mListener = listener;
    }

    /**
     * Update the suggestion strip with new suggestions.
     *
     * @param suggestions List of suggestions to display
     */
    public void setSuggestions(List<Suggestion> suggestions) {
        if (mSuggestionChipsContainer == null) {
            return;
        }

        // Clear existing chips
        mSuggestionChipsContainer.removeAllViews();
        mSuggestionChips.clear();

        if (suggestions == null || suggestions.isEmpty()) {
            // Show placeholder or hide strip
            addPlaceholderChip();
            return;
        }

        // Add suggestion chips
        int count = Math.min(MAX_VISIBLE_SUGGESTIONS, suggestions.size());
        for (int i = 0; i < count; i++) {
            final Suggestion suggestion = suggestions.get(i);
            addSuggestionChip(suggestion, i == 0); // First suggestion is highlighted
        }
    }

    /**
     * Add a suggestion chip to the container.
     *
     * @param suggestion The suggestion to display
     * @param isFirst Whether this is the first (primary) suggestion
     */
    private void addSuggestionChip(final Suggestion suggestion, boolean isFirst) {
        TextView chip = new TextView(mContext);

        // Set text
        chip.setText(suggestion.getWord());

        // Set styling
        chip.setGravity(android.view.Gravity.CENTER);
        chip.setPadding(
            dpToPx(16), // start
            dpToPx(0),  // top
            dpToPx(16), // end
            dpToPx(0)   // bottom
        );
        chip.setTextSize(14);
        chip.setClickable(true);
        chip.setFocusable(true);

        // Set background
        chip.setBackgroundResource(R.drawable.suggestion_chip_background);

        // Set text color
        chip.setTextColor(mContext.getResources().getColor(R.color.key_text_color_lxx_system));

        // Make first suggestion bold/prominent
        if (isFirst) {
            chip.setTypeface(null, android.graphics.Typeface.BOLD);
            chip.setTextSize(15); // Slightly larger
        }

        // Set layout params
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            dpToPx(36)
        );
        params.setMarginEnd(dpToPx(8));
        chip.setLayoutParams(params);

        // Set click listener
        chip.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mListener != null) {
                    mListener.onSuggestionClicked(suggestion);
                }
            }
        });

        // Add to container
        mSuggestionChipsContainer.addView(chip);
        mSuggestionChips.add(chip);
    }

    /**
     * Add a placeholder chip when no suggestions are available.
     */
    private void addPlaceholderChip() {
        TextView chip = new TextView(mContext);
        chip.setText("");
        chip.setGravity(android.view.Gravity.CENTER);
        chip.setPadding(dpToPx(16), dpToPx(0), dpToPx(16), dpToPx(0));
        chip.setTextSize(14);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            dpToPx(36)
        );
        chip.setLayoutParams(params);

        mSuggestionChipsContainer.addView(chip);
    }

    /**
     * Clear all suggestions from the strip.
     */
    public void clear() {
        if (mSuggestionChipsContainer != null) {
            mSuggestionChipsContainer.removeAllViews();
            mSuggestionChips.clear();
            addPlaceholderChip();
        }
    }

    /**
     * Show the suggestion strip.
     */
    public void show() {
        View suggestionStrip = ((View) mSuggestionChipsContainer.getParent());
        if (suggestionStrip != null) {
            suggestionStrip.setVisibility(View.VISIBLE);
        }
    }

    /**
     * Hide the suggestion strip.
     */
    public void hide() {
        View suggestionStrip = ((View) mSuggestionChipsContainer.getParent());
        if (suggestionStrip != null) {
            suggestionStrip.setVisibility(View.GONE);
        }
    }

    /**
     * Convert dp to pixels.
     */
    private int dpToPx(int dp) {
        float density = mContext.getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }
}
