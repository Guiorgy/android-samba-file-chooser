package com.obsez.android.lib.smbfilechooser.demo;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.obsez.android.lib.smbfilechooser.FileChooserDialog;
import com.obsez.android.lib.smbfilechooser.SmbFileChooserDialog;
import com.obsez.android.lib.smbfilechooser.internals.UiUtil;

import java.io.File;
import java.util.ArrayList;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import jcifs.smb.SmbException;
import jcifs.smb.SmbFile;

/**
 * A placeholder fragment containing a simple view.
 */
public class ChooseFileActivityFragment extends Fragment implements View.OnClickListener, CompoundButton.OnCheckedChangeListener {
    @SuppressWarnings("unused")
    private static final String TAG = "ChooseFileActivityFragment";

    private CheckBox disableTitle;
    private CheckBox enableOptions;
    private CheckBox enableSamba;
    private CheckBox enableMultiple;
    private CheckBox displayPath;
    private CheckBox dirOnly;
    private CheckBox allowHidden;
    private CheckBox continueFromLast;
    private CheckBox filterImages;
    private CheckBox displayIcon;
    private CheckBox dateFormat;
    private CheckBox darkTheme;
    private CheckBox dpad;

    private String _server = "smb://";
    private String _path = null;
    private TextView _tv;
    private ImageView _iv;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        //return super.onCreateView(inflater, container, savedInstanceState);

        View root = inflater.inflate(R.layout.fragment_choose_file, container, false);

        _tv = root.findViewById(R.id.textView);
        _tv.setText(BuildConfig.VERSION_NAME);
        _iv = root.findViewById(R.id.imageView);

        enableSamba = root.findViewById(R.id.checkbox_enable_samba);
        enableOptions = root.findViewById(R.id.checkbox_enable_options);
        disableTitle = root.findViewById(R.id.checkbox_disable_title);
        enableMultiple = root.findViewById(R.id.checkbox_enable_multiple);
        displayPath = root.findViewById(R.id.checkbox_display_path);
        dirOnly = root.findViewById(R.id.checkbox_dir_only);
        allowHidden = root.findViewById(R.id.checkbox_allow_hidden);
        continueFromLast = root.findViewById(R.id.checkbox_continue_from_last);
        filterImages = root.findViewById(R.id.checkbox_filter_images);
        displayIcon = root.findViewById(R.id.checkbox_display_icon);
        dateFormat = root.findViewById(R.id.checkbox_date_format);
        darkTheme = root.findViewById(R.id.checkbox_dark_theme);
        dpad = root.findViewById(R.id.checkbox_dpad);

        enableSamba.setOnCheckedChangeListener(this);
        root.findViewById(R.id.btn_show_dialog).setOnClickListener(this);

        return root;
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        if (isChecked) {
            AlertDialog.Builder alert;
            alert = darkTheme.isChecked() ? new AlertDialog.Builder(getContext(), R.style.FileChooserDialogStyle_Dark) : new AlertDialog.Builder(getContext(), R.style.FileChooserDialogStyle);
            alert.setTitle("Set server");
            alert.setCancelable(false);
            final EditText input = new EditText(getContext());
            input.setText(_server);
            alert.setView(input);
            alert.setPositiveButton("Ok", (dialog, whichButton) -> _server = input.getText().toString());
            alert.show();

            ViewGroup.LayoutParams params = input.getLayoutParams();
            ((ViewGroup.MarginLayoutParams) params).setMargins(
                UiUtil.dip2px(20),
                UiUtil.dip2px(10),
                UiUtil.dip2px(20),
                UiUtil.dip2px(10));
            input.setLayoutParams(params);
        }
    }

    @Override
    public void onClick(View v) {
        //choose a file
        final Context ctx = this.getActivity();
        assert ctx != null;

        if (enableSamba.isChecked()) {
            SmbFileChooserDialog smbFileChooserDialog = SmbFileChooserDialog.newDialog(ctx, _server)
                .setResources(R.string.title_choose_folder, R.string.title_choose, R.string.dialog_cancel)
                .setOptionResources(R.string.option_create_folder, R.string.option_refresh, R.string.options_delete, R.string.new_folder_cancel, R.string.new_folder_ok)
                .disableTitle(disableTitle.isChecked())
                .enableOptions(enableOptions.isChecked())
                .displayPath(displayPath.isChecked())
                .setOnChosenListener((dir, dirFile) -> {
                    if (continueFromLast.isChecked()) {
                        _path = dir;
                    }
                    try {
                        Toast.makeText(ctx, (dirFile.isDirectory() ? "FOLDER: " : "FILE: ") + dir, Toast.LENGTH_SHORT).show();
                        _tv.setText(dir);
                    } catch (SmbException e) {
                        e.printStackTrace();
                    }
                })
                .setExceptionHandler((exception, id) -> {
                    Toast.makeText(ctx, "Please, check your internet connection", Toast.LENGTH_SHORT).show();
                    return true;
                });
            if (darkTheme.isChecked()) {
                smbFileChooserDialog.setTheme(R.style.FileChooserStyle_Dark);
            }
            if (filterImages.isChecked()) {
                // Most common image file extensions (source: http://preservationtutorial.library.cornell.edu/presentation/table7-1.html)
                smbFileChooserDialog.setFilter(dirOnly.isChecked(),
                    allowHidden.isChecked(),
                    "tif", "tiff", "gif", "jpeg", "jpg", "jif", "jfif",
                    "jp2", "jpx", "j2k", "j2c", "fpx", "pcd", "png", "pdf");
            } else {
                smbFileChooserDialog.setFilter(dirOnly.isChecked(), allowHidden.isChecked());
            }
            if (enableMultiple.isChecked()) {
                smbFileChooserDialog.enableMultiple(true, false);
                smbFileChooserDialog.setOnSelectedListener(files -> {
                    ArrayList<String> paths = new ArrayList<>();
                    for (SmbFile file : files) {
                        paths.add(file.getPath());
                    }

                    AlertDialog.Builder builder = darkTheme.isChecked() ? new AlertDialog.Builder(ctx, R.style.FileChooserDialogStyle_Dark) : new AlertDialog.Builder(ctx, R.style.FileChooserDialogStyle);
                    builder.setTitle(files.size() + " files selected:")
                        .setAdapter(new ArrayAdapter<>(ctx,
                            android.R.layout.simple_expandable_list_item_1, paths), null)
                        .create()
                        .show();
                });
            }
            if (continueFromLast.isChecked() && _path != null) {
                smbFileChooserDialog.setStartFile(_path);
            }
            if (displayIcon.isChecked()) {
                smbFileChooserDialog.setIcon(R.mipmap.ic_launcher);
            }
            if (dateFormat.isChecked()) {
                smbFileChooserDialog.setDateFormat("dd MMMM yyyy");
            }
            if (dpad.isChecked()) {
                smbFileChooserDialog.enableDpad(true);
            }
            smbFileChooserDialog.show();
        } else {
            FileChooserDialog fileChooserDialog = FileChooserDialog.newDialog(ctx)
                .setResources(R.string.title_choose_folder, R.string.title_choose, R.string.dialog_cancel)
                .setOptionResources(R.string.option_create_folder, R.string.options_delete, R.string.new_folder_cancel, R.string.new_folder_ok)
                .disableTitle(disableTitle.isChecked())
                .enableOptions(enableOptions.isChecked())
                .displayPath(displayPath.isChecked())
                .setOnChosenListener((dir, dirFile) -> {
                    if (continueFromLast.isChecked()) {
                        _path = dir;
                    }
                    Toast.makeText(ctx, (dirFile.isDirectory() ? "FOLDER: " : "FILE: ") + dir, Toast.LENGTH_LONG).show();
                    _tv.setText(dir);
                    if (dirFile.isFile()) _iv.setImageBitmap(ImageUtil.decodeFile(dirFile));
                });
            if (darkTheme.isChecked()) {
                fileChooserDialog.setTheme(R.style.FileChooserStyle_Dark);
            }
            if (filterImages.isChecked()) {
                // Most common image file extensions (source: http://preservationtutorial.library.cornell.edu/presentation/table7-1.html)
                fileChooserDialog.setFilter(dirOnly.isChecked(),
                    allowHidden.isChecked(),
                    "tif", "tiff", "gif", "jpeg", "jpg", "jif", "jfif",
                    "jp2", "jpx", "j2k", "j2c", "fpx", "pcd", "png", "pdf");
            } else {
                fileChooserDialog.setFilter(dirOnly.isChecked(), allowHidden.isChecked());
            }
            if (enableMultiple.isChecked()) {
                fileChooserDialog.enableMultiple(true, false);
                fileChooserDialog.setOnSelectedListener(files -> {
                    ArrayList<String> paths = new ArrayList<>();
                    for (File file : files) {
                        paths.add(file.getAbsolutePath());
                    }

                    AlertDialog.Builder builder = darkTheme.isChecked() ? new AlertDialog.Builder(ctx, R.style.FileChooserDialogStyle_Dark) : new AlertDialog.Builder(ctx, R.style.FileChooserDialogStyle);
                    builder.setTitle(files.size() + " files selected:")
                        .setAdapter(new ArrayAdapter<>(ctx,
                            android.R.layout.simple_expandable_list_item_1, paths), null)
                        .create()
                        .show();
                });
            }
            if (continueFromLast.isChecked() && _path != null) {
                fileChooserDialog.setStartFile(_path);
            }
            if (displayIcon.isChecked()) {
                fileChooserDialog.setIcon(R.mipmap.ic_launcher);
            }
            if (dateFormat.isChecked()) {
                fileChooserDialog.setDateFormat("dd MMMM yyyy");
            }
            if (dpad.isChecked()) {
                fileChooserDialog.enableDpad(true);
            }
            fileChooserDialog.show();
        }
    }
}
