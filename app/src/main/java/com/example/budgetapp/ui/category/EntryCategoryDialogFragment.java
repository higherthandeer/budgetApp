package com.example.budgetapp.ui.category;

import android.app.Application;
import android.app.Dialog;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.text.*;
import android.view.*;
import android.widget.*;
import androidx.annotation.*;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.*;
import androidx.lifecycle.*;
import com.google.android.material.button.MaterialButton;
import com.example.budgetapp.*;
import com.example.budgetapp.data.*;
import com.example.budgetapp.data.entity.CategoryEntity;
import com.example.budgetapp.processing.LedgerTypes.Kind;
import com.example.budgetapp.ui.common.*;

/** Creates a category without committing or replacing the surrounding bill draft. */
public final class EntryCategoryDialogFragment extends DialogFragment {
    public static final String RESULT = "entry.category.created";
    public static final String ID = "categoryId", FLOW = "flow", TOKEN = "editorToken", NAME = "name";
    private static final String TAG = "entry_category_create";
    private Draft draft;
    private View form;
    private EditText name;
    private MaterialButton choose;
    private AlertDialog dialog, iconDialog;
    private boolean delivered;

    public static void show(FragmentManager manager, String kind, String editorToken) {
        if (manager.isStateSaved() || manager.findFragmentByTag(TAG) != null) return;
        EntryCategoryDialogFragment page = new EntryCategoryDialogFragment();
        Bundle args = new Bundle();
        args.putString(FLOW, kind);
        args.putString(TOKEN, editorToken);
        page.setArguments(args);
        page.showNow(manager, TAG);
    }

    @NonNull @Override public Dialog onCreateDialog(@Nullable Bundle savedState) {
        draft = new ViewModelProvider(this).get(Draft.class);
        form = getLayoutInflater().inflate(R.layout.dialog_category_create, null);
        form.findViewById(R.id.category_flow).setVisibility(View.GONE);
        name = form.findViewById(R.id.category_name_input);
        name.setText(draft.name());
        name.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) { draft.name(s.toString()); }
            public void afterTextChanged(Editable text) {}
        });
        choose = form.findViewById(R.id.category_icon_choose);
        choose.setBackgroundTintList(ColorStateList.valueOf(requireContext().getColor(R.color.obs_mint)));
        choose.setTextColor(requireContext().getColor(R.color.obs_green));
        choose.setIconTint(ColorStateList.valueOf(requireContext().getColor(R.color.obs_green)));
        choose.setOnClickListener(v -> iconDialog = CategoryIconPicker.show(requireContext(), draft.icon.getValue(), key -> draft.icon.setValue(key)));
        String flow = getString("INCOME".equals(requireArguments().getString(FLOW)) ? R.string.entry_income : R.string.entry_expense);
        dialog = new AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.entry_category_create_title, flow)).setView(form)
                .setNegativeButton(R.string.entry_cancel, null)
                .setPositiveButton(R.string.entry_category_create_use, null).create();
        draft.icon.observe(this, key -> {
            choose.setIconResource(CategoryIcons.forKey(key));
            choose.setText(getString(R.string.entry_category_choose_icon, CategoryIconCatalog.label(key)));
        });
        draft.error.observe(this, error -> name.setError(error == null ? null : getString(error)));
        draft.busy.observe(this, busy -> updateEnabled());
        draft.created.observe(this, created -> deliver());
        return dialog;
    }

    @Override public void onStart() {
        super.onStart();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> draft.create(Kind.valueOf(requireArguments().getString(FLOW))));
        updateEnabled();
    }

    @Override public void onResume() { super.onResume(); deliver(); }

    private void updateEnabled() {
        if (dialog == null || dialog.getButton(AlertDialog.BUTTON_POSITIVE) == null) return;
        boolean ready = !Boolean.TRUE.equals(draft.busy.getValue());
        name.setEnabled(ready);
        choose.setEnabled(ready);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(ready);
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(ready);
        setCancelable(ready);
        dialog.setCanceledOnTouchOutside(ready);
    }

    private void deliver() {
        CategoryEntity category = draft.created.getValue();
        if (delivered || category == null || !isAdded() || getParentFragmentManager().isStateSaved()) return;
        delivered = true;
        Bundle result = new Bundle();
        result.putString(ID, category.id);
        result.putString(NAME, category.name);
        result.putString(FLOW, category.flowScope);
        result.putString(TOKEN, requireArguments().getString(TOKEN));
        getParentFragmentManager().setFragmentResult(RESULT, result);
        dismiss();
    }

    @Override public void onDestroyView() {
        if (iconDialog != null) { iconDialog.dismiss(); iconDialog = null; }
        super.onDestroyView();
        form = null; name = null; choose = null; dialog = null;
    }

    /** Fragment-scoped work and saved fields survive rotation without repeating a write. */
    public static final class Draft extends AndroidViewModel {
        private final AppContainer app;
        private final SavedStateHandle state;
        private boolean active = true;
        public final MutableLiveData<String> icon;
        public final MutableLiveData<Boolean> busy = new MutableLiveData<>(false);
        public final MutableLiveData<Integer> error = new MutableLiveData<>();
        public final MutableLiveData<CategoryEntity> created = new MutableLiveData<>();

        public Draft(Application application, SavedStateHandle state) {
            super(application);
            app = ((BudgetApplication) application).container();
            this.state = state;
            icon = state.getLiveData("category.icon", "category");
        }
        String name() { String value = state.get("category.name"); return value == null ? "" : value; }
        void name(String value) { state.set("category.name", value); error.setValue(null); }
        void create(Kind kind) {
            if (Boolean.TRUE.equals(busy.getValue()) || created.getValue() != null) return;
            String value = CategoryRepository.trimCategoryName(name()), key = icon.getValue();
            if (value.isEmpty() || value.length() > 30) { error.setValue(R.string.category_name_invalid); return; }
            busy.setValue(true); error.setValue(null);
            app.io.execute(() -> {
                CategoryEntity category = null;
                Integer failure = null;
                try { category = app.categories.createCategory(value, kind, key); }
                catch (CategoryRepository.DuplicateCategoryName duplicate) { failure = R.string.category_name_exists; }
                catch (RuntimeException unavailable) { failure = R.string.category_failed; }
                CategoryEntity saved = category; Integer message = failure;
                app.main.execute(() -> {
                    if (!active) return;
                    busy.setValue(false);
                    if (saved != null) created.setValue(saved); else error.setValue(message);
                });
            });
        }
        @Override protected void onCleared() { active = false; super.onCleared(); }
    }
}
