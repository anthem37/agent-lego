package com.agentlego.backend.runtime;

import com.agentlego.backend.model.support.ChatModelFactory;
import com.agentlego.backend.runtime.definition.AgentDefinition;
import com.agentlego.backend.runtime.definition.ModelDefinition;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.Event;
import io.agentscope.core.memory.InMemoryMemory;
import io.agentscope.core.message.Msg;
import io.agentscope.core.rag.Knowledge;
import io.agentscope.core.rag.RAGMode;
import io.agentscope.core.rag.model.RetrieveConfig;
import io.agentscope.core.tool.Toolkit;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Objects;

/**
 * 平台侧对对话智能体运行时的封装。
 * <p>
 * 注意：运行时实例有状态；每次请求新建实例。
 */
@Component
public class AgentRuntime {

    private static final String USER_MESSAGE_NAME = "user";

    private final ChatModelFactory chatModelFactory;

    public AgentRuntime(ChatModelFactory chatModelFactory) {
        this.chatModelFactory = chatModelFactory;
    }

    private static Msg userMessage(String userText) {
        return Msg.builder()
                .name(USER_MESSAGE_NAME)
                .textContent(userText)
                .build();
    }

    public Mono<Msg> call(AgentDefinition agentDef, String userText, Toolkit toolkit) {
        Objects.requireNonNull(agentDef, "agentDef");
        Objects.requireNonNull(userText, "userText");

        ReActAgent agent = buildAgent(agentDef, toolkit);
        return agent.call(userMessage(userText));
    }

    public Flux<Event> stream(AgentDefinition agentDef, String userText, Toolkit toolkit) {
        Objects.requireNonNull(agentDef, "agentDef");
        Objects.requireNonNull(userText, "userText");

        ReActAgent agent = buildAgent(agentDef, toolkit);
        return agent.stream(userMessage(userText));
    }

    private ReActAgent buildAgent(AgentDefinition agentDef, Toolkit toolkit) {
        ModelDefinition modelDef = Objects.requireNonNull(agentDef.model(), "agentDef.model");

        Toolkit effectiveToolkit = (toolkit != null) ? toolkit : new Toolkit();
        InMemoryMemory memory = new InMemoryMemory();

        ReActAgent.Builder builder = ReActAgent.builder()
                .name(agentDef.name())
                .sysPrompt(agentDef.systemPrompt())
                .model(chatModelFactory.from(modelDef))
                .toolkit(effectiveToolkit)
                .memory(memory);

        if (agentDef.maxIters() != null) {
            builder.maxIters(agentDef.maxIters());
        }

        Knowledge knowledge = agentDef.knowledge();
        if (knowledge != null) {
            builder.knowledge(knowledge)
                    .ragMode(RAGMode.GENERIC)
                    .retrieveConfig(RetrieveConfig.builder()
                            .limit(agentDef.knowledgeTopK())
                            .scoreThreshold(agentDef.knowledgeScoreThreshold())
                            .build());
        }

        return builder.build();
    }
}

