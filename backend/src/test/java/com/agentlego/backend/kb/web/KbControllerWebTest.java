package com.agentlego.backend.kb.web;

import com.agentlego.backend.api.GlobalExceptionHandler;
import com.agentlego.backend.kb.application.dto.KbCollectionDeleteResult;
import com.agentlego.backend.kb.application.service.KbApplicationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = KbController.class)
@Import(GlobalExceptionHandler.class)
class KbControllerWebTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private KbApplicationService kbApplicationService;

    @Test
    void deleteDocument_ok_shouldCallService() throws Exception {
        mockMvc.perform(delete("/kb/collections/c1/documents/d1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"));

        verify(kbApplicationService).deleteDocument("c1", "d1");
    }

    @Test
    void deleteCollection_ok_shouldCallService() throws Exception {
        when(kbApplicationService.deleteCollection("c1")).thenReturn(new KbCollectionDeleteResult(2));

        mockMvc.perform(delete("/kb/collections/c1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.agentsPolicyUpdated").value(2));

        verify(kbApplicationService).deleteCollection("c1");
    }
}
