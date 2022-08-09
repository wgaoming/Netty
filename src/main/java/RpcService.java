import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import lombok.extern.slf4j.Slf4j;
import protocol.MessageCodecSharable;
import protocol.ProcotolFrameDecoder;
import service.handler.RpcRequestMessageHandler;

@Slf4j
public class RpcService {
    public static void main(String[] args) throws InterruptedException {
        //创建NioEventLoop对象，在下面服务器启动后添加创建，boos用于处理可连接事件，worker处理可读事件
        NioEventLoopGroup boss=new NioEventLoopGroup();
        NioEventLoopGroup worker=new NioEventLoopGroup();
        //创建日志处理器
        LoggingHandler LOGGING_HANDLER = new LoggingHandler(LogLevel.DEBUG);
        //创建编解码器对象
        MessageCodecSharable MESSAGE_CODEC = new MessageCodecSharable();
        // 自定义rpc 请求消息处理器
        RpcRequestMessageHandler RPC_HANDLER = new RpcRequestMessageHandler();
        //半包黏包处理器
        ProcotolFrameDecoder procotolFrameDecoder=new ProcotolFrameDecoder();
        try{
            //服务器启动器，整合netty的各种组件
            ServerBootstrap serverBootstrap=new ServerBootstrap()
                    //选择一种ssc实现，这里选择niossc实现
                    .channel(NioServerSocketChannel.class)
                    //创建NioEventLoopGroup，内部包含selector与线程池
                    .group(boss,worker)
                    //决定child（worker）能执行哪些操作（handler）
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) throws Exception {
                            //添加下列处理器
                            ch.pipeline().addLast(LOGGING_HANDLER);
                            ch.pipeline().addLast(MESSAGE_CODEC);
                            ch.pipeline().addLast(RPC_HANDLER);
                            ch.pipeline().addLast(procotolFrameDecoder);
                        }
                    });
            //绑定服务 端口
            Channel channel=serverBootstrap.bind(8080).sync().channel();
            channel.closeFuture().sync();
        }catch (InterruptedException e) {
            log.error("server error", e);
        } finally {
            boss.shutdownGracefully();
            worker.shutdownGracefully();
        }
    }
}
