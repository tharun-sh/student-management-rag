package com.github.tharun_sh.student_management_rag_app;

import com.github.tharun_sh.student_management_rag_app.service.rag.ChatService;
import com.github.tharun_sh.student_management_rag_app.service.rag.VectorSyncService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@ActiveProfiles("test")
class StudentManagementRagAppApplicationTests {

	@MockitoBean
	VectorSyncService vectorSyncService;

	@MockitoBean
	ChatService chatService;

	@Test
	void contextLoads() {
	}

}
