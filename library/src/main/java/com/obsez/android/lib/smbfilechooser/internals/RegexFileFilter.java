package com.obsez.android.lib.smbfilechooser.internals;

import java.io.File;
import java.io.FileFilter;
import java.util.regex.Pattern;

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

public class RegexFileFilter implements FileFilter {
    private boolean m_allowHidden;
    private boolean m_onlyDirectory;
    private Pattern m_pattern;

    public RegexFileFilter() {
        this(null);
    }

    public RegexFileFilter(Pattern ptn) {
        this(false, false, ptn);
    }

    public RegexFileFilter(boolean dirOnly, boolean hidden, String ptn) {
        m_allowHidden = hidden;
        m_onlyDirectory = dirOnly;
        m_pattern = Pattern.compile(ptn, Pattern.CASE_INSENSITIVE);
    }

    public RegexFileFilter(boolean dirOnly, boolean hidden, String ptn, int flags) {
        m_allowHidden = hidden;
        m_onlyDirectory = dirOnly;
        m_pattern = Pattern.compile(ptn, flags);
    }

    public RegexFileFilter(boolean dirOnly, boolean hidden, Pattern ptn) {
        m_allowHidden = hidden;
        m_onlyDirectory = dirOnly;
        m_pattern = ptn;
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

        if (m_pattern == null) {
            return true;
        }

        if (pathname.isDirectory()) {
            return true;
        }

        String name = pathname.getName();
        return m_pattern.matcher(name).matches();
    }

}
