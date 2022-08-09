import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.util.concurrent.DefaultPromise;
import lombok.extern.slf4j.Slf4j;
import message.RpcRequestMessage;
import protocol.MessageCodecSharable;
import protocol.ProcotolFrameDecoder;
import protocol.SequenceIdGenerator;
import service.HelloService;
import service.handler.RpcResponseMessageHandler;

import java.lang.reflect.Proxy;

@Slf4j
public class RpcClient {
    public static void main(String[] args) {
        HelloService service = getProxyService(HelloService.class);
        System.out.println(service.sayHello("zhangsan"));
    }

    // 创建代理类,，将一般方法调用转换成RPC请求消息，并调用channel发送消息
    public static <T> T getProxyService(Class<T> serviceClass) {
        ClassLoader loader = serviceClass.getClassLoader();
        Class<?>[] interfaces = new Class[]{serviceClass};
        //  使用jdk代理，创建代理对象
        Object o = Proxy.newProxyInstance(loader, interfaces, (proxy, method, args) -> {
            // 1. 创建请求消息，将方法调用转换为消息对象
            int sequenceId = SequenceIdGenerator.nextId();
            RpcRequestMessage msg = new RpcRequestMessage(
                    sequenceId,
                    serviceClass.getName(),
                    method.getName(),
                    method.getReturnType(),
                    method.getParameterTypes(),
                    args
            );
            // 2. 调用下面的方法获取channel，并将消息对象发送出去
            getChannel().writeAndFlush(msg);

            // 3. 准备一个空 Promise 对象，来接收结果，指定 promise 对象异步接收结果线程
            DefaultPromise<Object> promise = new DefaultPromise<>(getChannel().eventLoop());
            RpcResponseMessageHandler.PROMISES.put(sequenceId, promise);
            // 4. 等待 promise 结果
            promise.await();
            if(promise.isSuccess()) {
                // 调用正常
                return promise.getNow();
            } else {
                // 调用失败
                throw new RuntimeException(promise.cause());
            }
        });
        return (T) o;
    }

    private static final Object LOCK = new Object();
    // 获取唯一的 channel 对象
    public static Channel getChannel() {
        if (channel != null) {
            return channel;
        }
        synchronized (LOCK) { //  t2
            if (channel != null) { // t1
                return channel;
            }
            initChannel();
            return channel;
        }
    }
    private static Channel channel = null;
    private static void initChannel() {
        //创建selector启动Netty
        NioEventLoopGroup group = new NioEventLoopGroup();
        //创建日志处理器
        LoggingHandler LOGGING_HANDLER = new LoggingHandler(LogLevel.DEBUG);
        //创建编解码器对象
        MessageCodecSharable MESSAGE_CODEC = new MessageCodecSharable();
        // 自定义rpc 响应消息处理器
        RpcResponseMessageHandler RPC_HANDLER = new RpcResponseMessageHandler();
        //半包黏包处理器
        ProcotolFrameDecoder procotolFrameDecoder=new ProcotolFrameDecoder();
        //启动器类
        Bootstrap bootstrap = new Bootstrap();
        //选择niossc
        bootstrap.channel(NioSocketChannel.class);
        bootstrap.group(group);
        bootstrap.handler(new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel ch) throws Exception {
                ch.pipeline().addLast(procotolFrameDecoder);
                ch.pipeline().addLast(LOGGING_HANDLER);
                ch.pipeline().addLast(MESSAGE_CODEC);
                ch.pipeline().addLast(RPC_HANDLER);
            }
        });
        try {
            channel = bootstrap.connect("localhost", 8080).sync().channel();
            //非阻塞关闭
            channel.closeFuture().addListener(future -> {
                group.shutdownGracefully();
            });
        } catch (Exception e) {log.error("client error", e);}
    }
}