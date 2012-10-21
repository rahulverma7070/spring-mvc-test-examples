package net.petrikainulainen.spring.testmvc.todo.controller;

import net.petrikainulainen.spring.testmvc.todo.ToDoTestUtil;
import net.petrikainulainen.spring.testmvc.todo.config.UnitTestContext;
import net.petrikainulainen.spring.testmvc.todo.dto.ToDoDTO;
import net.petrikainulainen.spring.testmvc.todo.exception.ToDoNotFoundException;
import net.petrikainulainen.spring.testmvc.todo.model.ToDo;
import net.petrikainulainen.spring.testmvc.todo.service.ToDoService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.context.MessageSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.validation.BindingResult;
import org.springframework.validation.Validator;
import org.springframework.validation.support.BindingAwareModelMap;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertNull;
import static junit.framework.Assert.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

/**
 * @author Petri Kainulainen
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = {UnitTestContext.class})
public class ToDoControllerTest {

    private static final String FEEDBACK_MESSAGE = "feedbackMessage";
    private static final String FIELD_DESCRIPTION = "description";
    private static final String FIELD_TITLE = "title";

    private ToDoController controller;

    private MessageSource messageSourceMock;

    private ToDoService serviceMock;

    @Resource
    private Validator validator;

    @Before
    public void setUp() {
        controller = new ToDoController();

        messageSourceMock = mock(MessageSource.class);
        ReflectionTestUtils.setField(controller, "messageSource", messageSourceMock);

        serviceMock = mock(ToDoService.class);
        ReflectionTestUtils.setField(controller, "service", serviceMock);
    }

    @Test
    public void showAddToDoForm() {
        BindingAwareModelMap model = new BindingAwareModelMap();

        String view = controller.showAddToDoForm(model);

        verifyZeroInteractions(messageSourceMock, serviceMock);
        assertEquals(ToDoController.VIEW_TODO_ADD, view);

        ToDoDTO formObject = (ToDoDTO) model.asMap().get(ToDoController.MODEL_ATTRIBUTE_TODO);

        assertNull(formObject.getId());
        assertNull(formObject.getDescription());
        assertNull(formObject.getTitle());
    }

    @Test
    public void add() {
        ToDoDTO formObject = ToDoTestUtil.createDTO(null, ToDoTestUtil.DESCRIPTION, ToDoTestUtil.TITLE);

        ToDo model = ToDoTestUtil.createModel(ToDoTestUtil.ID, ToDoTestUtil.DESCRIPTION, ToDoTestUtil.TITLE);
        when(serviceMock.add(formObject)).thenReturn(model);

        MockHttpServletRequest mockRequest = new MockHttpServletRequest("POST", "/todo/add");
        BindingResult result = bindAndValidate(mockRequest, formObject);

        RedirectAttributesModelMap attributes = new RedirectAttributesModelMap();

        initMessageSourceForFeedbackMessage(ToDoController.FEEDBACK_MESSAGE_KEY_TODO_ADDED);

        String view = controller.add(formObject, result, attributes);

        verify(serviceMock, times(1)).add(formObject);
        verifyNoMoreInteractions(serviceMock);

        String expectedView = ToDoTestUtil.createRedirectViewPath(ToDoController.REQUEST_MAPPING_TODO_VIEW);
        assertEquals(expectedView, view);

        assertEquals(Long.valueOf((String) attributes.get(ToDoController.PARAMETER_TODO_ID)), model.getId());

        assertFeedbackMessage(attributes, ToDoController.FEEDBACK_MESSAGE_KEY_TODO_ADDED);
    }

    @Test
    public void addEmptyTodo() {
        ToDoDTO formObject = ToDoTestUtil.createDTO(null, "", "");

        MockHttpServletRequest mockRequest = new MockHttpServletRequest("POST", "/todo/add");
        BindingResult result = bindAndValidate(mockRequest, formObject);

        RedirectAttributesModelMap attributes = new RedirectAttributesModelMap();

        String view = controller.add(formObject, result, attributes);

        verifyZeroInteractions(serviceMock, messageSourceMock);

        assertEquals(ToDoController.VIEW_TODO_ADD, view);
        assertFieldErrors(result, FIELD_TITLE);
    }

    @Test
    public void addToDoWithTooLongDescriptionAndTitle() {
        String description = ToDoTestUtil.createStringWithLength(ToDo.MAX_LENGTH_DESCRIPTION + 1);
        String title = ToDoTestUtil.createStringWithLength(ToDo.MAX_LENGTH_TITLE + 1);

        ToDoDTO formObject = ToDoTestUtil.createDTO(null, description, title);

        MockHttpServletRequest mockRequest = new MockHttpServletRequest("POST", "/todo/add");
        BindingResult result = bindAndValidate(mockRequest, formObject);

        RedirectAttributesModelMap attributes = new RedirectAttributesModelMap();

        String view = controller.add(formObject, result, attributes);

        verifyZeroInteractions(serviceMock, messageSourceMock);

        assertEquals(ToDoController.VIEW_TODO_ADD, view);
        assertFieldErrors(result, FIELD_DESCRIPTION, FIELD_TITLE);
    }

    @Test
    public void deleteById() throws ToDoNotFoundException {
        RedirectAttributesModelMap attributes = new RedirectAttributesModelMap();

        ToDo model = ToDoTestUtil.createModel(ToDoTestUtil.ID, ToDoTestUtil.DESCRIPTION, ToDoTestUtil.TITLE);
        when(serviceMock.deleteById(ToDoTestUtil.ID)).thenReturn(model);

        initMessageSourceForFeedbackMessage(ToDoController.FEEDBACK_MESSAGE_KEY_TODO_DELETED);

        String view = controller.deleteById(ToDoTestUtil.ID, attributes);

        verify(serviceMock, times(1)).deleteById(ToDoTestUtil.ID);
        verifyNoMoreInteractions(serviceMock);

        assertFeedbackMessage(attributes, ToDoController.FEEDBACK_MESSAGE_KEY_TODO_DELETED);

        String expectedView = ToDoTestUtil.createRedirectViewPath(ToDoController.REQUEST_MAPPING_TODO_LIST);
        assertEquals(expectedView, view);
    }

    @Test(expected = ToDoNotFoundException.class)
    public void deleteByIdWhenToDoIsNotFound() throws ToDoNotFoundException {
        RedirectAttributesModelMap attributes = new RedirectAttributesModelMap();

        when(serviceMock.deleteById(ToDoTestUtil.ID)).thenThrow(new ToDoNotFoundException(""));

        controller.deleteById(ToDoTestUtil.ID, attributes);

        verify(serviceMock, times(1)).deleteById(ToDoTestUtil.ID);
        verifyNoMoreInteractions(serviceMock);
        verifyZeroInteractions(messageSourceMock);
    }

    @Test
    public void findAll() {
        BindingAwareModelMap model = new BindingAwareModelMap();

        List<ToDo> models = new ArrayList<ToDo>();
        when(serviceMock.findAll()).thenReturn(models);

        String view = controller.findAll(model);

        verify(serviceMock, times(1)).findAll();
        verifyNoMoreInteractions(serviceMock);
        verifyZeroInteractions(messageSourceMock);

        assertEquals(ToDoController.VIEW_TODO_LIST, view);
        assertEquals(models, model.asMap().get(ToDoController.MODEL_ATTRIBUTE_TODO_LIST));
    }

    @Test
    public void findById() throws ToDoNotFoundException {
        BindingAwareModelMap model = new BindingAwareModelMap();

        ToDo found = ToDoTestUtil.createModel(ToDoTestUtil.ID, ToDoTestUtil.DESCRIPTION, ToDoTestUtil.TITLE);
        when(serviceMock.findById(ToDoTestUtil.ID)).thenReturn(found);

        String view = controller.findById(ToDoTestUtil.ID, model);

        verify(serviceMock, times(1)).findById(ToDoTestUtil.ID);
        verifyNoMoreInteractions(serviceMock);
        verifyZeroInteractions(messageSourceMock);

        assertEquals(ToDoController.VIEW_TODO_VIEW, view);
        assertEquals(found, model.asMap().get(ToDoController.MODEL_ATTRIBUTE_TODO));
    }

    @Test(expected = ToDoNotFoundException.class)
    public void findByIdWhenToDoIsNotFound() throws ToDoNotFoundException {
        BindingAwareModelMap model = new BindingAwareModelMap();

        when(serviceMock.findById(ToDoTestUtil.ID)).thenThrow(new ToDoNotFoundException(""));

        controller.findById(ToDoTestUtil.ID, model);

        verify(serviceMock, times(1)).findById(ToDoTestUtil.ID);
        verifyNoMoreInteractions(serviceMock);
        verifyZeroInteractions(messageSourceMock);
    }

    @Test
    public void showUpdateToDoForm() throws ToDoNotFoundException {
        BindingAwareModelMap model = new BindingAwareModelMap();

        ToDo updated = ToDoTestUtil.createModel(ToDoTestUtil.ID, ToDoTestUtil.DESCRIPTION, ToDoTestUtil.TITLE);
        when(serviceMock.findById(ToDoTestUtil.ID)).thenReturn(updated);

        String view = controller.showUpdateToDoForm(ToDoTestUtil.ID, model);

        verify(serviceMock, times(1)).findById(ToDoTestUtil.ID);
        verifyNoMoreInteractions(serviceMock);
        verifyZeroInteractions(messageSourceMock);

        assertEquals(ToDoController.VIEW_TODO_UPDATE, view);

        ToDoDTO formObject = (ToDoDTO) model.asMap().get(ToDoController.MODEL_ATTRIBUTE_TODO);

        assertEquals(updated.getId(), formObject.getId());
        assertEquals(updated.getDescription(), formObject.getDescription());
        assertEquals(updated.getTitle(), formObject.getTitle());
    }

    @Test(expected = ToDoNotFoundException.class)
    public void showUpdateToDoFormWhenToDoIsNotFound() throws ToDoNotFoundException {
        BindingAwareModelMap model = new BindingAwareModelMap();

        when(serviceMock.findById(ToDoTestUtil.ID)).thenThrow(new ToDoNotFoundException(""));

        controller.showUpdateToDoForm(ToDoTestUtil.ID, model);

        verify(serviceMock, times(1)).findById(ToDoTestUtil.ID);
        verifyNoMoreInteractions(serviceMock);
        verifyZeroInteractions(messageSourceMock);
    }

    @Test
    public void update() throws ToDoNotFoundException {
        ToDoDTO formObject = ToDoTestUtil.createDTO(ToDoTestUtil.ID, ToDoTestUtil.DESCRIPTION_UPDATED, ToDoTestUtil.TITLE_UPDATED);

        ToDo model = ToDoTestUtil.createModel(ToDoTestUtil.ID, ToDoTestUtil.DESCRIPTION_UPDATED, ToDoTestUtil.TITLE_UPDATED);
        when(serviceMock.update(formObject)).thenReturn(model);

        MockHttpServletRequest mockRequest = new MockHttpServletRequest("POST", "/todo/add");
        BindingResult result = bindAndValidate(mockRequest, formObject);

        RedirectAttributesModelMap attributes = new RedirectAttributesModelMap();

        initMessageSourceForFeedbackMessage(ToDoController.FEEDBACK_MESSAGE_KEY_TODO_UPDATED);

        String view = controller.update(formObject, result, attributes);

        verify(serviceMock, times(1)).update(formObject);
        verifyNoMoreInteractions(serviceMock);

        String expectedView = ToDoTestUtil.createRedirectViewPath(ToDoController.REQUEST_MAPPING_TODO_VIEW);
        assertEquals(expectedView, view);

        assertEquals(Long.valueOf((String) attributes.get(ToDoController.PARAMETER_TODO_ID)), model.getId());

        assertFeedbackMessage(attributes, ToDoController.FEEDBACK_MESSAGE_KEY_TODO_UPDATED);
    }

    @Test
    public void updateEmptyToDo() throws ToDoNotFoundException {
        ToDoDTO formObject = ToDoTestUtil.createDTO(ToDoTestUtil.ID, "", "");

        MockHttpServletRequest mockRequest = new MockHttpServletRequest("POST", "/todo/add");
        BindingResult result = bindAndValidate(mockRequest, formObject);

        RedirectAttributesModelMap attributes = new RedirectAttributesModelMap();

        String view = controller.update(formObject, result, attributes);

        verifyZeroInteractions(messageSourceMock, serviceMock);

        assertEquals(ToDoController.VIEW_TODO_UPDATE, view);
        assertFieldErrors(result, FIELD_TITLE);
    }

    @Test
    public void updateToDoWhenDescriptionAndTitleAreTooLong() throws ToDoNotFoundException {
        String description = ToDoTestUtil.createStringWithLength(ToDo.MAX_LENGTH_DESCRIPTION + 1);
        String title = ToDoTestUtil.createStringWithLength(ToDo.MAX_LENGTH_TITLE + 1);

        ToDoDTO formObject = ToDoTestUtil.createDTO(ToDoTestUtil.ID, description, title);

        MockHttpServletRequest mockRequest = new MockHttpServletRequest("POST", "/todo/add");
        BindingResult result = bindAndValidate(mockRequest, formObject);

        RedirectAttributesModelMap attributes = new RedirectAttributesModelMap();

        String view = controller.update(formObject, result, attributes);

        verifyZeroInteractions(messageSourceMock, serviceMock);

        assertEquals(ToDoController.VIEW_TODO_UPDATE, view);
        assertFieldErrors(result, FIELD_DESCRIPTION, FIELD_TITLE);
    }

    @Test(expected = ToDoNotFoundException.class)
    public void updateWhenToDoIsNotFound() throws ToDoNotFoundException {
        ToDoDTO formObject = ToDoTestUtil.createDTO(ToDoTestUtil.ID, ToDoTestUtil.DESCRIPTION_UPDATED, ToDoTestUtil.TITLE_UPDATED);

        when(serviceMock.update(formObject)).thenThrow(new ToDoNotFoundException(""));

        MockHttpServletRequest mockRequest = new MockHttpServletRequest("POST", "/todo/add");
        BindingResult result = bindAndValidate(mockRequest, formObject);

        RedirectAttributesModelMap attributes = new RedirectAttributesModelMap();

        controller.update(formObject, result, attributes);

        verify(serviceMock, times(1)).update(formObject);
        verifyNoMoreInteractions(serviceMock);
        verifyZeroInteractions(messageSourceMock);
    }

    private void assertFeedbackMessage(RedirectAttributes attributes, String messageCode) {
        assertFlashMessages(attributes, messageCode, ToDoController.FLASH_MESSAGE_KEY_FEEDBACK);
    }

    private void assertFieldErrors(BindingResult result, String... fieldNames) {
        assertEquals(fieldNames.length, result.getFieldErrorCount());
        for (String fieldName : fieldNames) {
            assertNotNull(result.getFieldError(fieldName));
        }
    }

    private void assertFlashMessages(RedirectAttributes attributes, String messageCode, String flashMessageParameterName) {
        Map<String, ?> flashMessages = attributes.getFlashAttributes();
        Object message = flashMessages.get(flashMessageParameterName);

        assertNotNull(message);
        flashMessages.remove(message);
        assertTrue(flashMessages.isEmpty());

        verify(messageSourceMock, times(1)).getMessage(eq(messageCode), any(Object[].class), any(Locale.class));
        verifyNoMoreInteractions(messageSourceMock);
    }

    private BindingResult bindAndValidate(HttpServletRequest request, Object formObject) {
        WebDataBinder binder = new WebDataBinder(formObject);
        binder.setValidator(validator);
        binder.bind(new MutablePropertyValues(request.getParameterMap()));
        binder.getValidator().validate(binder.getTarget(), binder.getBindingResult());
        return binder.getBindingResult();
    }

    private void initMessageSourceForFeedbackMessage(String feedbackMessageCode) {
        when(messageSourceMock.getMessage(eq(feedbackMessageCode), any(Object[].class), any(Locale.class))).thenReturn(FEEDBACK_MESSAGE);
    }
}