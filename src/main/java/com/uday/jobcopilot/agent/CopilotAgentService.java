package com.uday.jobcopilot.agent;

import com.google.adk.agents.LlmAgent;
import com.google.adk.agents.RunConfig;
import com.google.adk.events.Event;
import com.google.adk.models.BaseLlm;
import com.google.adk.models.Gemini;
import com.google.adk.runner.InMemoryRunner;
import com.google.adk.tools.AgentTool;
import com.google.adk.tools.FunctionTool;
import com.google.genai.Client;
import com.google.genai.types.Content;
import com.google.genai.types.HttpOptions;
import com.google.genai.types.HttpRetryOptions;
import com.google.genai.types.Part;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Builds the agent team and runs conversations.
 *
 * <pre>
 *            copilot (coordinator)
 *   ┌────────────┼─────────────────┐
 *   tracker tools  answer_writer   outreach_writer
 * </pre>
 *
 * The coordinator talks to you, keeps the tracker up to date, and hands writing jobs to the two
 * specialist agents (wrapped as tools, so the coordinator stays in charge of the conversation).
 */
@Service
public class CopilotAgentService {

    private static final String APP = "job-copilot";
    private static final String USER = "me";

    private final InMemoryRunner runner;
    private final String model;
    private final boolean apiKeyConfigured;

    /**
     * @param apiKey read from the .env file (imported in application.properties) or the
     *               GOOGLE_API_KEY / GEMINI_API_KEY environment variable.
     */
    public CopilotAgentService(CopilotTools tools,
                               @Value("${copilot.model}") String model,
                               @Value("${GOOGLE_API_KEY:${GEMINI_API_KEY:}}") String apiKey) {
        this.model = model;
        this.apiKeyConfigured = !apiKey.isBlank();
        // One shared Gemini client for all agents. Without a key, chat fails with a clear error.
        // Timeout: a stuck Gemini call fails with an error instead of hanging the request forever.
        // attempts(1): no automatic retries, which silently burn the small free-tier daily quota.
        BaseLlm llm = apiKeyConfigured
                ? new Gemini(model, Client.builder()
                        .apiKey(apiKey.strip())
                        .httpOptions(HttpOptions.builder()
                                .timeout(90_000)
                                .retryOptions(HttpRetryOptions.builder().attempts(1).build())
                                .build())
                        .build())
                : null;
        FunctionTool getResume = FunctionTool.create(tools, "getResume");

        LlmAgent answerWriter = agentBuilder(llm)
                .name("answer_writer")
                .description("Writes answers to job application / screening questions using the user's resume. "
                        + "Pass the exact question, plus the job title and description if known.")
                .instruction("""
                        You write answers to job application questions for the user, in the first person.
                        1. Always call get_resume first. Use ONLY facts from the resume; never invent
                           experience, companies, numbers or skills. If something is missing, write around it.
                        2. Pick the 2-3 resume items most relevant to the question and job description, and
                           reuse the job description's keywords where they are true for the user.
                        3. Default length: 80-150 words unless the question implies otherwise
                           (e.g. a one-line field, a salary, a yes/no).
                        4. Plain, confident, specific. No buzzword filler, no "I am writing to".
                        Return only the answer text, ready to paste.
                        """)
                .tools(getResume)
                .build();

        LlmAgent outreachWriter = agentBuilder(llm)
                .name("outreach_writer")
                .description("Writes short cold outreach messages (LinkedIn connection notes, DMs, emails, "
                        + "follow-ups) to founders, recruiters or engineers.")
                .instruction("""
                        You write short outreach messages for the user's job search.
                        1. Call get_resume first and use only true facts from it.
                        2. LinkedIn connection notes must be under 300 characters. DMs/emails: 60-120 words.
                        3. Structure: who the user is in one line, one specific relevant project, a clear ask
                           (a quick chat or referral), polite close. Mention the company/role by name.
                        4. Follow-ups: two to three sentences, friendly, reference the earlier application.
                        Return only the message, ready to send. Never claim it was sent.
                        """)
                .tools(getResume)
                .build();

        LlmAgent copilot = agentBuilder(llm)
                .name("copilot")
                .description("The user's job hunt copilot.")
                .instruction("""
                        You are the user's job hunt copilot. Be brief and practical.

                        SCOPE: only help with the user's job search and career:
                        applications and the tracker, resume and profile questions, screening answers,
                        cover letters, outreach and follow-ups, interview preparation (what to expect,
                        behavioural answers, which topics to revise, how to explain their projects),
                        salary and offer questions, and choosing between roles or companies.
                        Anything else (writing or solving code, general knowledge, news, homework,
                        chit-chat) is out of scope: reply in one friendly sentence that you only help
                        with the job hunt, and suggest one job-related thing you can do instead.
                        Do not answer the out-of-scope part even partly.

                        - Never mention tool or agent names (like list_applications or answer_writer)
                          to the user; describe what you did in plain words.
                        - If a request has blanks or placeholders like [company] or [role], ask for
                          the missing details instead of guessing.
                        - Tracker: when the user says they applied somewhere, got an interview, were rejected,
                          etc., update the tracker with the tools right away and confirm in one line.
                          Find ids with list_applications before updating. Never delete anything.
                        - When asked "what's pending", "follow-ups" or "what should I do today", call
                          follow_ups_due and list_applications, then give a short prioritised to-do list.
                        - For application/screening questions, call answer_writer with the question and any
                          job details, then return its answer unchanged.
                        - For LinkedIn messages, emails to recruiters or follow-up messages, call outreach_writer.
                        - Questions about the user's background: call get_resume; never guess.
                        - You cannot browse the web, submit applications or send messages; say so if asked.
                        Format replies in simple Markdown.
                        """)
                .tools(
                        getResume,
                        FunctionTool.create(tools, "listApplications"),
                        FunctionTool.create(tools, "addApplication"),
                        FunctionTool.create(tools, "updateApplication"),
                        FunctionTool.create(tools, "followUpsDue"),
                        AgentTool.create(answerWriter),
                        AgentTool.create(outreachWriter))
                .build();

        this.runner = new InMemoryRunner(copilot, APP);
    }

    public record ChatReply(String reply, List<String> steps) {}

    /** Sends one message to the agent team and waits for the final answer. */
    public ChatReply chat(String sessionId, String message) {
        Content userMsg = Content.builder().role("user").parts(List.of(Part.fromText(message))).build();
        RunConfig config = RunConfig.builder().autoCreateSession(true).build();

        StringBuilder reply = new StringBuilder();
        List<String> steps = new ArrayList<>();
        for (Event event : runner.runAsync(USER, sessionId, userMsg, config).blockingIterable()) {
            for (Part part : event.content().flatMap(Content::parts).orElse(List.of())) {
                part.functionCall().ifPresent(call ->
                        steps.add(event.author() + " → " + call.name().orElse("?")));
                boolean thought = part.thought().orElse(false);
                if (event.finalResponse() && !thought && "copilot".equals(event.author())) {
                    part.text().ifPresent(reply::append);
                }
            }
        }
        return new ChatReply(reply.toString().strip(), steps);
    }

    public String model() {
        return model;
    }

    public boolean apiKeyConfigured() {
        return apiKeyConfigured;
    }

    /** Uses the explicit Gemini client when a key was found, else lets ADK resolve the model name. */
    private LlmAgent.Builder agentBuilder(BaseLlm llm) {
        return llm != null ? LlmAgent.builder().model(llm) : LlmAgent.builder().model(model);
    }
}
