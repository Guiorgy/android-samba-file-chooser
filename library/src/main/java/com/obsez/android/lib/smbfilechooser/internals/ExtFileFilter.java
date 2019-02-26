package com.obsez.android.lib.smbfilechooser.internals;

import java.io.File;
import java.io.FileFilter;

/**
 * Created by coco on 6/7/15.
 * Copyright 2015-2019 Hedzr Yeh
 * Modified 2018-2019 Guiorgy
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
public class ExtFileFilter implements FileFilter {
    private boolean m_allowHidden;
    private boolean m_onlyDirectory;
    private String[] m_ext;

    public ExtFileFilter() {
        this(false, false);
    }

    public ExtFileFilter(String... ext_list) {
        this(false, false, ext_list);
    }

    public ExtFileFilter(boolean dirOnly, boolean hidden, String... ext_list) {
        m_allowHidden = hidden;
        m_onlyDirectory = dirOnly;
        m_ext = ext_list;
    }

    @Override
    public boolean accept(File pathname) {
        if (!m_allowHidden) {
            if (pathname.isHidden()) {
                return false;
            }
        }

        if (m_onlyDirectory) {
            if (!pathname.isDirectory()) {
                return false;
            }
        }

        if (m_ext == null) {
            return true;
        }

        if (pathname.isDirectory()) {
            return true;
        }

        String ext = FileUtil.getExtensionWithoutDot(pathname);
        for (String e : m_ext) {
            if (ext.equalsIgnoreCase(e)) {
                return true;
            }
        }
        return false;
    }

}
