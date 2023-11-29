package br.com.wlabs.hayiz.doc.listener.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ProxyUtil {

    private static final List<Proxy> proxies = new ArrayList<>();

    private static Logger log = LoggerFactory.getLogger(ProxyUtil.class);

    static {
        proxies.add(new Proxy(Proxy.Type.HTTP, new InetSocketAddress("177.107.194.169", 8080)));
        //proxies.add(new Proxy(Proxy.Type.HTTP, new InetSocketAddress("201.91.82.155", 3128)));
    }

    public static ProxySelector proxySelector() {
        return new ProxySelector() {
            @Override
            public List<Proxy> select(URI uri) {
                /*if (uri.getHost().contains("acesso.gov.br")) {
                    return proxies;
                }*/
                return Collections.emptyList();
            }

            @Override
            public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {
                log.error("URI " + uri + " SocketAddress " + sa);
                ioe.printStackTrace();
            }
        };
    }
}
