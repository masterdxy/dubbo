package com.alibaba.dubbo.rpc.listener;

import com.alibaba.dubbo.common.extension.ExtensionLoader;
import com.alibaba.dubbo.rpc.ExporterListener;
import org.junit.Test;

/**
 * Created by tomoyo on 2017/7/3.
 */
public class ExportListenerTest {

    private static ExporterListener exporterListener = ExtensionLoader.getExtensionLoader(ExporterListener.class).getDefaultExtension();

    @Test
    public void name() throws Exception {
        System.out.println(exporterListener);
    }
}
