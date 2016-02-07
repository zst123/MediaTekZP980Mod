/*
 * Copyright (C) 2016 zst123
 * Copyright (C) 2013 XuiMod
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


package com.zst.xposed.zopo980phonemod;

import android.content.res.XModuleResources;

import de.robv.android.xposed.IXposedHookInitPackageResources;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.callbacks.XC_InitPackageResources.InitPackageResourcesParam;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

public class Xposed implements IXposedHookInitPackageResources, IXposedHookZygoteInit, IXposedHookLoadPackage {
	
	static XModuleResources modRes;
	
	@Override
	public void handleInitPackageResources(InitPackageResourcesParam resparam) throws Throwable {
		CameraMod.loadRes(resparam);
	}
	
	@Override
	public void initZygote(StartupParam startupParam) throws Throwable {
		modRes = XModuleResources.createInstance(startupParam.modulePath, null);
	}
	
	@Override
	public void handleLoadPackage(LoadPackageParam lpparam) throws Throwable {
		ScrollerMod.load();
		SecondsClockMod.handleLoadPackage(lpparam);
		CameraMod.fixPref(lpparam);
	}
	
}
