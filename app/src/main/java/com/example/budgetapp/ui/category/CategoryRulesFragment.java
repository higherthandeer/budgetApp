package com.example.budgetapp.ui.category;

import android.os.Bundle;
import android.view.*;
import android.widget.*;

import androidx.annotation.*;
import androidx.fragment.app.Fragment;
import androidx.appcompat.app.AlertDialog;

import com.example.budgetapp.*;
import com.example.budgetapp.data.*;
import com.example.budgetapp.data.entity.*;
import com.example.budgetapp.processing.LedgerTypes;

import java.util.*;

public final class CategoryRulesFragment extends Fragment {
    private AppContainer app;
    private AlertDialog createDialog;
    private AlertDialog iconDialog;

    public CategoryRulesFragment() {
        super(R.layout.fragment_category_rules);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        app = ((BudgetApplication) requireActivity().getApplication()).container();
        reload();
    }

    private void reload() {
        View page = getView();
        if (page == null) return;
        app.io.execute(() -> {
            List<CategoryEntity> categories = app.categories.categories();
            List<CategoryRuleEntity> rules = app.categories.rules();
            app.main.execute(() -> {
                if (getView() != page) return;
                LinearLayout content = page.findViewById(R.id.category_content);
                content.removeAllViews();
                TextView title = new TextView(requireContext());
                title.setText(R.string.category_manage);
                title.setTextSize(24);
                content.addView(title);
                Button back = new Button(requireContext());
                back.setText(R.string.entry_cancel);
                back.setOnClickListener(v -> requireActivity().getSupportFragmentManager().popBackStack());
                content.addView(back);
                com.google.android.material.button.MaterialButton add = new com.google.android.material.button.MaterialButton(requireContext());
                add.setId(R.id.category_add);
                add.setText(R.string.category_create_title);
                add.setOnClickListener(v -> createCategory());
                content.addView(add);
                Map<String, String> names = new HashMap<>();
                for (var c : categories) {
                    names.put(c.id, c.name);
                    LinearLayout row = new LinearLayout(requireContext());
                    row.setOrientation(LinearLayout.VERTICAL);
                    TextView label = new TextView(requireContext());
                    String categoryLabel=getString(R.string.category_flow_label,c.name,getString("INCOME".equals(c.flowScope)?R.string.entry_income:R.string.entry_expense));
                    label.setText(c.archived ? getString(R.string.category_archived, categoryLabel) : categoryLabel);
                    label.setTextSize(18);
                    row.addView(label);
                    com.google.android.material.button.MaterialButton icon=new com.google.android.material.button.MaterialButton(requireContext());
                    icon.setText("更换图标");icon.setIconResource(com.example.budgetapp.ui.common.CategoryIcons.forKey(c.iconKey));icon.setContentDescription("更换图标："+c.name);
                    icon.setOnClickListener(v->iconDialog=com.example.budgetapp.ui.common.CategoryIconPicker.show(requireContext(),c.iconKey,key->perform(()->app.categories.setCategoryIcon(c.id,key))));
                    row.addView(icon);
                    Button rename = new Button(requireContext());
                    rename.setText(R.string.category_rename);
                    rename.setOnClickListener(v -> {
                        EditText input = new EditText(requireContext());
                        input.setText(c.name);
                        new AlertDialog.Builder(requireContext()).setTitle(R.string.category_rename).setView(input).setNegativeButton(R.string.entry_cancel, null).setPositiveButton(R.string.category_apply, (d, w) -> perform(() -> app.categories.renameCategory(c.id, input.getText().toString()))).show();
                    });
                    row.addView(rename);
                    if (!c.archived && !c.id.equals(LedgerTypes.UNCATEGORIZED_EXPENSE) && !c.id.equals(LedgerTypes.UNCATEGORIZED_INCOME)) {
                        Button archive = new Button(requireContext());
                        archive.setText(R.string.category_archive);
                        archive.setOnClickListener(v -> new AlertDialog.Builder(requireContext()).setMessage(c.name).setNegativeButton(R.string.entry_cancel, null).setPositiveButton(R.string.category_archive, (d, w) -> perform(() -> app.categories.archiveCategory(c.id))).show());
                        row.addView(archive);
                    }
                    content.addView(row);
                }
                if (rules.isEmpty()) {
                    TextView empty = new TextView(requireContext());
                    empty.setText(R.string.category_no_rules);
                    content.addView(empty);
                }
                for (var r : rules) {
                    View row = getLayoutInflater().inflate(R.layout.item_category_rule, content, false);
                    CheckBox enabled = row.findViewById(R.id.rule_enabled);
                    enabled.setText(getString(R.string.category_rule_label, r.merchantPattern, names.get(r.categoryId)));
                    enabled.setChecked(r.enabled);
                    enabled.setOnCheckedChangeListener((button, value) -> perform(() -> app.categories.setRuleEnabled(r.id, value)));
                    row.findViewById(R.id.rule_preview).setOnClickListener(v -> preview(r.id));
                    content.addView(row);
                }
            });
        });
    }

    private void createCategory() {
        if (createDialog != null && createDialog.isShowing()) return;
        View page=getView();
        View form=getLayoutInflater().inflate(R.layout.dialog_category_create,null);
        EditText input=form.findViewById(R.id.category_name_input);
        RadioGroup flow=form.findViewById(R.id.category_flow);
        String[] selectedIcon={"category"};
        com.google.android.material.button.MaterialButton choose=form.findViewById(R.id.category_icon_choose);
        choose.setIconResource(com.example.budgetapp.ui.common.CategoryIcons.forKey(selectedIcon[0]));
        choose.setOnClickListener(v->iconDialog=com.example.budgetapp.ui.common.CategoryIconPicker.show(requireContext(),selectedIcon[0],key->{selectedIcon[0]=key;choose.setIconResource(com.example.budgetapp.ui.common.CategoryIcons.forKey(key));choose.setText("选择图标："+CategoryIconCatalog.label(key));}));
        AlertDialog dialog=new AlertDialog.Builder(requireContext()).setTitle(R.string.category_create_title).setView(form)
                .setNegativeButton(R.string.entry_cancel,null).setPositiveButton(R.string.category_create_save,null).create();
        createDialog=dialog;
        dialog.setOnDismissListener(d->{if(createDialog==dialog)createDialog=null;});
        dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            String name=CategoryRepository.trimCategoryName(input.getText().toString());
            if(name.isEmpty()||name.length()>30){input.setError(getString(R.string.category_name_invalid));return;}
            LedgerTypes.Kind kind=flow.getCheckedRadioButtonId()==R.id.category_flow_income?LedgerTypes.Kind.INCOME:LedgerTypes.Kind.EXPENSE;
            String iconKey=selectedIcon[0];
            creationBusy(dialog,form,true);
            app.io.execute(()->{
                int error=0;
                try{app.categories.createCategory(name,kind,iconKey);}
                catch(CategoryRepository.DuplicateCategoryName duplicate){error=R.string.category_name_exists;}
                catch(RuntimeException failure){error=R.string.category_failed;}
                int message=error;
                app.main.execute(()->{
                    if(getView()!=page||!dialog.isShowing())return;
                    if(message==0){dialog.dismiss();reload();}
                    else{creationBusy(dialog,form,false);input.setError(getString(message));}
                });
            });
        }));
        dialog.show();
    }

    private void creationBusy(AlertDialog dialog,View form,boolean busy) {
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(!busy);
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(!busy);
        dialog.setCancelable(!busy);
        dialog.setCanceledOnTouchOutside(!busy);
        for(int id:new int[]{R.id.category_name_input,R.id.category_flow_expense,R.id.category_flow_income,R.id.category_icon_choose})form.findViewById(id).setEnabled(!busy);
    }

    @Override public void onDestroyView() {
        if(iconDialog!=null){iconDialog.dismiss();iconDialog=null;}
        if(createDialog!=null){createDialog.dismiss();createDialog=null;}
        super.onDestroyView();
    }

    private void preview(String id) {
        app.io.execute(() -> {
            try {
                var preview = app.categories.previewReclassification(id);
                app.main.execute(() -> {
                    if (!isAdded()) return;
                    new AlertDialog.Builder(requireContext()).setMessage(getString(R.string.category_preview_count, preview.changes.size())).setNegativeButton(R.string.entry_cancel, null).setPositiveButton(R.string.category_apply, (d, w) -> app.io.execute(() -> {
                        try {
                            var result = app.categories.applyReclassification(preview);
                            app.main.execute(() -> {
                                if (!isAdded()) return;
                                Toast.makeText(requireContext(), result.status == ManualLedgerRepository.WriteResult.Status.APPLIED ? R.string.category_done : R.string.category_stale, Toast.LENGTH_LONG).show();
                                reload();
                            });
                        } catch (RuntimeException e) {
                            failed();
                        }
                    })).show();
                });
            } catch (RuntimeException e) {
                failed();
            }
        });
    }

    private void perform(Runnable action) {
        app.io.execute(() -> {
            try {
                action.run();
                app.main.execute(() -> {
                    if (isAdded()) reload();
                });
            } catch (RuntimeException e) {
                failed();
            }
        });
    }

    private void failed() {
        app.main.execute(() -> {
            if (isAdded())
                Toast.makeText(requireContext(), R.string.category_failed, Toast.LENGTH_LONG).show();
        });
    }
}
