package com.lyh.liuaiagent.agent;

import com.lyh.liuaiagent.agent.model.AgentState;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import opennlp.tools.util.StringUtil;
import org.apache.catalina.User;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 抽象基础代理类，用于管理代理状态和执行流程
 *
 * 提供状态转换，内存管理和基于步骤的执行循环的基础功能
 */
@Data
@Slf4j
public abstract class BaseAgent {

    //核心属性
    private String name;

    //提示
    private String systemPrompt;
    private String nextStepPrompt;

    //状态
    private AgentState state=AgentState.IDLE;

    //执行控制
    private int maxSteps=10;
    private int currentStep=0;

    //LLM
    private ChatClient chatClient;

    //Memory(需要自主维护回话上下文)
    private List<Message> messageList=new ArrayList<>();

    /**
     * 运行代理
     * @param userPrompt 用户提示词
     * @return 执行结果
     */
    public String run(String userPrompt){
        if(this.state!=AgentState.IDLE){
            throw new RuntimeException("Cannot run agent from state: "+this.state);
        }
        if(StringUtil.isEmpty(userPrompt)){
            throw new RuntimeException("Cannot run agent with empty user prompt");
        }

        //更改状态
        state=AgentState.RUNNING;
        //记录消息上下文
        messageList.add(new UserMessage(userPrompt));
        //保存结果列表
        List<String> results=new ArrayList<>();

        try{
            for (int i = 0; i <maxSteps && state!=AgentState.FINISHED ; i++) {
                int stepNumber=i+1;
                currentStep=stepNumber;
                log.info("Executing step "+stepNumber +"/" +maxSteps);
                //单步执行
                String stepResult=step();
                String result="Step "+stepNumber+": "+stepResult;
                results.add(result);
            }
            //检查是否超出步骤限制
            if(currentStep>=maxSteps){
                state=AgentState.FINISHED;
                results.add("Terminated: Reached max steps( "+maxSteps+ ")");
            }
            return String.join("\n", results);
        } catch (Exception e) {
            state=AgentState.ERROR;
            log.info("Error executing agent",e);
            return "执行错误"+e.getMessage();
        }finally {
            this.cleanup();
        }
    }

    /**
     * 执行单个步骤
     * @return 步骤执行结果
     */
    public abstract String step();

    /**
     * 清理资源
     */
    protected void cleanup(){
        // 子类可以重写此方法清理资源
    }

    /** 可被会话服务订阅的逐步输出；取消订阅后不再开始下一个工具步骤。 */
    public reactor.core.publisher.Flux<String> runStreamEvents(String userPrompt) {
        return reactor.core.publisher.Flux.<String>create(sink -> {
            try {
                if (state != AgentState.IDLE) throw new IllegalStateException("智能体正在运行");
                state = AgentState.RUNNING;
                messageList.add(new UserMessage(userPrompt));
                String lastStepResult = "";
                for (int i = 0; i < maxSteps && state != AgentState.FINISHED && !sink.isCancelled(); i++) {
                    currentStep = i + 1;
                    // 工具参数、网页源码和命令输出只用于智能体内部推理，不能直接作为聊天正文发送。
                    lastStepResult = step();
                }
                if (!sink.isCancelled()) {
                    boolean reachedStepLimit = currentStep >= maxSteps && state != AgentState.FINISHED;
                    String userFacingResult = buildUserFacingResult(lastStepResult, reachedStepLimit);
                    if (userFacingResult != null && !userFacingResult.isBlank()) sink.next(userFacingResult);
                    state = AgentState.FINISHED;
                    sink.complete();
                }
            } catch (Exception error) {
                state = AgentState.ERROR;
                sink.error(error);
            } finally {
                cleanup();
            }
        }).subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic());
    }

    /**
     * 将内部最后一步转换为用户可见答复。工具型智能体可覆盖此方法，生成不含执行日志的摘要。
     */
    protected String buildUserFacingResult(String lastStepResult, boolean reachedStepLimit) {
        return lastStepResult;
    }

    /**
     * 运行代理（流式输出）
     *
     * @param userPrompt 用户提示词
     * @return SseEmitter实例
     */
    public SseEmitter runStream(String userPrompt) {
        // 创建SseEmitter，设置较长的超时时间
        SseEmitter emitter = new SseEmitter(300000L); // 5分钟超时

        // 使用线程异步处理，避免阻塞主线程
        CompletableFuture.runAsync(() -> {
            try {
                if (this.state != AgentState.IDLE) {
                    emitter.send("错误：无法从状态运行代理: " + this.state);
                    emitter.complete();
                    return;
                }
                if (StringUtil.isEmpty(userPrompt)) {
                    emitter.send("错误：不能使用空提示词运行代理");
                    emitter.complete();
                    return;
                }

                // 更改状态
                state = AgentState.RUNNING;
                // 记录消息上下文
                messageList.add(new UserMessage(userPrompt));

                try {
                    for (int i = 0; i < maxSteps && state != AgentState.FINISHED; i++) {
                        int stepNumber = i + 1;
                        currentStep = stepNumber;
                        log.info("Executing step " + stepNumber + "/" + maxSteps);

                        // 单步执行
                        String stepResult = step();
                        String result = "Step " + stepNumber + ": " + stepResult;

                        // 发送每一步的结果
                        emitter.send(result);
                    }
                    // 检查是否超出步骤限制
                    if (currentStep >= maxSteps) {
                        state = AgentState.FINISHED;
                        emitter.send("执行结束: 达到最大步骤 (" + maxSteps + ")");
                    }
                    // 正常完成
                    emitter.complete();
                } catch (Exception e) {
                    state = AgentState.ERROR;
                    log.error("执行智能体失败", e);
                    try {
                        emitter.send("执行错误: " + e.getMessage());
                        emitter.complete();
                    } catch (Exception ex) {
                        emitter.completeWithError(ex);
                    }
                } finally {
                    // 清理资源
                    this.cleanup();
                }
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        });

        // 设置超时和完成回调
        emitter.onTimeout(() -> {
            this.state = AgentState.ERROR;
            this.cleanup();
            log.warn("SSE connection timed out");
        });

        emitter.onCompletion(() -> {
            if (this.state == AgentState.RUNNING) {
                this.state = AgentState.FINISHED;
            }
            this.cleanup();
            log.info("SSE connection completed");
        });

        return emitter;
    }

}
