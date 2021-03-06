# RELEASES

## 1.4.8s - 2020-02-24

\- FileChooserDialog DEPRECATED!

## 1.4.7s - 2019-09-11

\* more localization for Bahasa Indonesia (thanks joielechong)
\* bug fixes (mainly #63, #75, #78)

## 1.4.6s - 2019-06-12

\- removed unnecessary dependency on kotlin-android plugin
\- removed dependency on android-maven-gradle-plugin (abandoned as of 2019-06-07)
\+ added localization for Bahasa Indonesia (thanks joielechong)
\+ added localization for Georgian

## 1.4.5s - 2019-06-10

\* fixed not scrolling to the top on PathView first draw
\* fixed AboutActivity and changed demo app banner and logo
\* explicitly removed transition animations from PermissionActivity
\* fixed hedzr#69 nullPointerException on monkeytest

## 1.4.4s - 2019-05-27

\* switched to AppCompat AlertDialog and AppCompat themes
\+ reimplemented the feature removed in 1.4.1s (now scrolls to top after refresh)
\+ implemented backtracking (scrolling to the directory you were in when going back) to SmbFileChooser
\* minor layout and scrolling fixes

## 1.4.3s - 2019-05-15

\* fully moved to AndroidX
\* moved to AppCompat themes

## 1.4.2s - 2019-04-22

\- removed refreshLayout
\+ instead, added refresh button in the options menu
\* bumped gradle
\* bugs fixed

## 1.4.1s - 2019-04-08

\+ fileListItemFocusedDrawable attribute added
\* bug fixes (especially with Dpad controls)
\+ FileChooser now remembers directory when going back
\- SmbFileChooser however no longer scrolls to top when list refreshes (the above feature should be added soon)

## 1.4.0s - 2019-03-25

\+ added FileChooser style. You can now set a custom theme
\+ overrideGetView added to adapter (accessed from AdapterSetter)
\* calling build() no longer obligatory
\+ now possible to pass Drawables instead of drawable resouces
\+ now dialog shows after granting read permission
\* many small improvements and bug fixes

## 1.3.4s - 2019-03-12

\+ enabled R8 shrinker
\* bump jcifs-ng from 2.1.1 to 2.1.2
\* removed the workaround introduced in commit 3b94230, release v1.3.1s, as the problem was fixed in eu.agno3.jcifs:jcifs-ng:2.1.2 thanks to mbechler

## 1.3.3s - 2019-03-01

\* fixed a bug that threw NullPointerException when selecting multiple files.
\* updated the demo app.

## 1.3.2s - 2019-03-01

\+ added support for SD storage. (same as in android-file-chooser)
\* displayPath(true) no longer sets dialog title to file name. Instead, path to the file is displayed below the title and above the list of files.
\+ added customizePathView method to customize the filePath displayed.

## 1.3.1s - 2019-02-28

\* fixed progressbar not centered when loading files.
\* fixed timeout when unable to connect to server.
\- removed setServer and setAuthenticator methods.
\+ now possible to pass custom properties to CIFSContext.

## 1.3.0s - 2019-02-26

\* !BREAKING! switched to the new jcifs library currently maintained: https://github.com/AgNO3/jcifs-ng
\* similarly, to support the new smb api, source and target compatibility now set to 1.8
\+ now scrolling is blocked when new files are being loaded
\+ added displayPath setting. if enabled (title must also be enabled), current path will be displayed in the title.
\* bug fix on api < 21

## 1.2.0s - 2018-10-22

\* changed NewFolderFilter
\+ added Delete mode indicator. When in Delete mode option button and Delete button become red
\+ added the ability to select multiple files
\+ added Dpad support for Android TVs
\* bug fixes


## 1.1.11s - 2018-09-14

\+ added **NtlmPasswordAuthentication** authentication to the *samba* client
\* updated the demo app


## 1.1.10s - 2018-09-14

\* forked as android-smbfile-chooser!
\* changed all methods that start with `with...` to start with `set...`
\+ added `OnDismissListener`(**only works on API >= 17**) and `OnBackPressedListener`
\* changed the default back button behaviour from always dismissing the dialog, to going up a directory
\+ added the ability to enable options button (AlertDialog Neutral button) to be able to create new folders and delete files on the go (for both local and shared Files)
\+ added the ability to set Strings instead of StringResources. **if both are set, StringResources take priority**

## 1.1.10 - 2018-06-05

bug fixed


## 1.1.9 - 2018-05-20

\+ #14, `withFileIcons(...)`, `withFileIconsRes(...)`, `withAdapterSetter(...)`


## 1.1.8 - 2018-05-17

\+ #13, `withRowLayoutView(resId)` allow color/font customizing...


## 1.1.7 - 2018-05-15

\* #8, misspell typo fixed, thx @bostrot
\* #9, new style constructor from @SeppPenner
\* README for #3 is more friendlier
\* KS_PATH usage in #2 is more friendlier


## 1.1.6 - 2018-02-11

Spring 2018 Version:

\+ permissions check form @bostrot, thx a lot
\* upgrade to AS 3 & gradle 4.1+
\* about #4, add 2: [withIcon()](./library/src/main/java/com/obsez/android/lib/filechooser/ChooserDialog.java#L114), [withLayoutView()](./library/src/main/java/com/obsez/android/lib/filechooser/ChooserDialog.java#L119)


## 1.1.5 - 2017-06-27

fixed issue #2


## 1.1.4.1 - 2017-06-27

bug fixed for last releases

\* navigate and choose a folder without extension cause crash


## 1.1.4 - 2016-12-31

bug fixed for last release

\* remove app_name;
\* dateformat fixed;


## 1.1.3 - 2016-12-30

\+ withDateFormat()
\* remove wrong timestamp display at '..' (parent) folder
\+ add history/changelog
\* upgrade building environment
\+ traditional chinese language


