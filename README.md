# android-file-chooser

## Overview

![banner](captures/banner.svg)

### android-file-chooser
[![Android Arsenal](https://img.shields.io/badge/Android%20Arsenal-android--file--chooser-brightgreen.svg?style=flat)](https://android-arsenal.com/details/1/6982)
[![Download](https://api.bintray.com/packages/hedzr/maven/filechooser/images/download.svg)](https://bintray.com/hedzr/maven/filechooser/_latestVersion)
[![Release](https://jitpack.io/v/hedzr/android-file-chooser.svg)](https://jitpack.io/#hedzr/android-file-chooser)
### android-smbfile-chooser
[![Relese](https://jitpack.io/v/Guiorgy/android-smbfile-chooser.svg)](https://jitpack.io/#Guiorgy/android-smbfile-chooser/)
[![API](https://img.shields.io/badge/API-14%2B-brightgreen.svg?style=flat)](https://android-arsenal.com/api?level=14)

`android-file-library` is a lightweight file/folder chooser.
This (`android-smbfile-chooser`) is my attempt to add the ability to use [jcifs.smb.SmbFile](https://github.com/AgNO3/jcifs-ng) to browse a Windows shared directory.

### Snapshots

<img src="captures/demo.gif" width="360"/><img src="captures/choose_folder_smb.png" width="360"/>
<img src="captures/tv_dpad_demo.gif" width="720"/>

### Demo Application

A demo-app of the original can be installed from [Play Store](https://play.google.com/store/apps/details?id=com.obsez.android.lib.filechooser.demo).

<a href='https://play.google.com/store/apps/details?id=com.obsez.android.lib.filechooser.demo&pcampaignid=MKT-Other-global-all-co-prtnr-py-PartBadge-Mar2515-1'><img alt='Get it on Google Play' width='240' src='https://play.google.com/intl/en_us/badges/images/generic/en_badge_web_generic.png'/></a>

**NOTE**:

Please don't forget to check the [**_upstream_**](https://github.com/hedzr/android-file-chooser) and give it a :star:!

If you are using  a different Support library, then use this:
```
configurations.all {
	resolutionStrategy.force 'com.android.support:appcompat-v7:x.y.z'
}
```

1.2.0 used jcifs 1.3.17, which only supports SMB1.\
1.3.0 however switched to an open source, maintained [jcifs-ng](https://github.com/AgNO3/jcifs-ng) which is a breaking change! This one fully supports SMB2, and partially SMB3.
Also, target sdk was bumped to 1.8:
```java
android {
    compileOptions {
        sourceCompatibility JavaVersion.VERSION_1_8
        targetCompatibility JavaVersion.VERSION_1_8
    }
}
```

## Usage

```java
SmbFileChooserDialog.newDialog(context, "**.***.*.**", authenticator)
    .setResources("select a directory", "choose", "cancel")
    .setFilter(/*only directories (no files)*/ true, /*don't show hidden files/folders*/ false)
    .setOnChosenListener((path, file) -> {
        String msg = "error";
        try{
            msg = file.isDirectory() ? "directory" : "file" + " selected: " + path
        } catch(SmbException e){
            e.printStackTrace();
        }
        // This is NOT main UI thread. you can NOT access SmbFiles on UI thread.
        Handler mainHandler = new Handler(context.getMainLooper());
        mainHandler.post(() -> {
            Toast.makeText(context,
                msg,
                Toast.LENGTH_SHORT)
            .show();
        });
    })
    .setExceptionHandler((exception, id) -> {
        Toast.makeText(context, exception.getMessage(), Toast.LENGTH_LONG).show();
        return true;
    })
    .show();
```

#### Additional options
```java
.displayPath(/*displays the current path in the title (title must be enabled)*/ true)
.enableOptions(/*enables 'New folder' and 'Delete'*/ true)
.setOptionResources("New folder", "Delete", "Cancel", "OK")
.setNewFolderFilter(new NewFolderFilter(/*max length of 10*/ 10, /*regex pattern that only allows a to z (lowercase)*/ "^[a-z]*$"))
.enableMultiple(/*enables the ability to select multiple*/ true, /*allows selecting folders along with files*/ true)
.setOnSelectedListener(/*this gets called, when user selects more than 1 file*/ (files) -> {
	ArrayList<String> paths = new ArrayList<String>();
	for (SmbFile file : files) paths.add(file.getPath());
	AlertDialog.Builder dialog = new AlertDialog.Builder(context);
	dialog.setTitle("Selected files:");
	dialog.setAdapter(new ArrayAdapter<String>(context, android.R.layout.simple_expandable_list_item_1, paths) , null);
	dialog.show();
})
.enableDpad(/*enables Dpad controls (mainly for Android TVs)*/ true)
.cancelOnTouchOutside(true)
.setTheme(R.style.FileChooserStyle)
.setAdapterSetter(adapter -> {
    adapter.overrideGetView(
        (file, isSelected, convertView, parent, inflater) -> { 
            // inflate and return view. SmbFile should not be accessed on the main thread!
            return convertView;
        }, (file, isSelected, view) -> { 
            // modify view. only available if SmbFileChooserDialog is being used.
        });
})
```

## What's Different?

I replaced all methods "with___()" with "set___()"! And, use static method "newDialog(context)" instead of a constructor.

- there are no public constructors. Instead use static methods:
```java
FileChooserDialog.newDialog(context)
```
- when multiple files are selected, a new listener is called:
```java
FileChooserDialog.setOnSelectedListener(files -> {
    ArrayList<String> paths = new ArrayList<>();
    for (File file : files) {
        paths.add(file.getPath());
    }

    new AlertDialog.Builder(context)
        .setTitle(files.size() + " files selected:")
        .setAdapter(new ArrayAdapter<>(context,
            android.R.layout.simple_expandable_list_item_1, paths), null)
        .create()
        .show();
});
```
- there's no _**titleFollowsDir**_ option, and _**displayPath**_ is false by default
- in _**SmbFileChooserDialog**_ use _**setExceptionHandler**_ to handle exceptions
```java
interface ExceptionHandler {
    /**
     * @return true to attempt to terminate.
     * false to attempt to ignore exception and continue
     */
    boolean handle(Throwable exception, int id);
}
```

For more information please refer to the [upstream repo](https://github.com/hedzr/android-file-chooser).

## License

Standard Apache 2.0

Copyright 2015-2019 Hedzr Yeh\
Modified 2018-2019 Guiorgy

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.