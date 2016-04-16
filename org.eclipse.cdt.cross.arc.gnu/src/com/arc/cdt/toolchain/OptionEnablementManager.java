/*******************************************************************************
 * Copyright (c) 2005, 2014 Synopsys, Incorporated
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * Synopsys, Inc. - Initial implementation
 * Synopsys, Inc. - ARC GNU Toolchain support
 *******************************************************************************/
package com.arc.cdt.toolchain;

import java.util.ArrayList;

public class OptionEnablementManager extends AbstractOptionEnablementManager {

	public OptionEnablementManager() {
		addObserver(new Observer());
	}

	class Observer implements IObserver {

        public void onOptionValueChanged(IOptionEnablementManager mgr, String optionId) {

            // Get suffix of option and make sure all with same suffix
            // are set to same value.
            // For example "arc.asm.options.arc5core" and
            // "arc.compiler.options.arc5core" must match.
            // HACK: except for ".level". We don't want optimization level to be
            // mistaken for
            // debug level!
            Object v = mgr.getValue(optionId);
            if (v instanceof String || v instanceof Boolean) {
                String suffix = getSuffixOf(optionId);
                // Make copy to avoid occasional ConcurrentModificationException
                for (String id : new ArrayList<String>(getOptionIds())) {
                    if (suffix.equals(getSuffixOf(id)) && !id.equals(optionId)
                            && !suffix.equals("level")) {
                        setOptionValue(id, v);
                    }
                }
            }
        }

		public void onOptionEnablementChanged(IOptionEnablementManager mgr, String optionID) {
			// @todo Auto-generated method stub
		}
	}

	protected static String getSuffixOf(String id) {
		int lastDot = id.lastIndexOf('.');
		if (lastDot >= 0) {
			return id.substring(lastDot + 1);
		}
		return id;
	}

}
