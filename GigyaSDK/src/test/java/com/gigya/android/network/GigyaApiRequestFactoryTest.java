package com.gigya.android.network;

import android.util.Base64;

import com.gigya.android.sdk.Config;
import com.gigya.android.sdk.Gigya;
import com.gigya.android.sdk.api.GigyaApiRequest;
import com.gigya.android.sdk.api.GigyaApiRequestFactory;
import com.gigya.android.sdk.network.adapter.RestAdapter;
import com.gigya.android.sdk.session.ISessionService;
import com.gigya.android.sdk.session.SessionInfo;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.HashMap;
import java.util.Map;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.powermock.api.mockito.PowerMockito.when;

@RunWith(PowerMockRunner.class)
@PrepareForTest({Base64.class})
@PowerMockIgnore("javax.crypto.*")
public class GigyaApiRequestFactoryTest {

    @Mock
    Config _config;

    @Mock
    ISessionService _sessionService;

    @Mock
    SessionInfo _sessionInfo;

    @InjectMocks
    GigyaApiRequestFactory _factory;

    @Before
    public void setup() {
        when(_config.getApiDomain()).thenReturn("us1.gigya.com");
        PowerMockito.mockStatic(Base64.class);
        PowerMockito.when(Base64.decode(anyString(), anyInt())).thenAnswer(new Answer<Object>() {
            @Override
            public Object answer(InvocationOnMock invocation) {
                return java.util.Base64.getMimeDecoder().decode((String) invocation.getArguments()[0]);
            }
        });
        PowerMockito.when(Base64.encode(any(byte[].class), anyInt())).thenAnswer(new Answer<Object>() {
            @Override
            public Object answer(InvocationOnMock invocation) {
                return java.util.Base64.getEncoder().encode((byte[]) invocation.getArguments()[0]);
            }
        });
        PowerMockito.when(Base64.encodeToString(any(byte[].class), anyInt())).thenAnswer(new Answer<Object>() {
            @Override
            public Object answer(InvocationOnMock invocation) {
                return new String(java.util.Base64.getEncoder().encode((byte[]) invocation.getArguments()[0]));
            }
        });
    }

    @Test
    public void testRequestUrl() {
        // Arrange
        final Map<String, Object> params = new HashMap<>();

        // Act
        final GigyaApiRequest request = _factory.create("accounts.getAccountInfo", params, RestAdapter.POST);

        // Assert
        assertNotNull(request);
        assertEquals("https://accounts.us1.gigya.com/accounts.getAccountInfo", request.getUrl());
    }

    @Test
    public void testDefaultRequestParameters() {
        // Arrange
        final Map<String, Object> params = new HashMap<>();

        // Act
        final GigyaApiRequest request = _factory.create("accounts.getAccountInfo", params, RestAdapter.POST);

        // Assert
        assertNotNull(request);
        assertNotNull(request.getEncodedParams());
        assertEquals("format=json&httpStatusCodes=false&sdk=" + Gigya.VERSION + "&targetEnv=mobile", request.getEncodedParams());
    }

    @Test
    public void testGmidParameter() {
        // Arrange
        final Map<String, Object> params = new HashMap<>();
        when(_config.getGmid()).thenReturn("mockGMID");

        // Act
        final GigyaApiRequest request = _factory.create("accounts.getAccountInfo", params, RestAdapter.POST);

        // Assert
        assertNotNull(request);
        assertNotNull(request.getEncodedParams());
        assertTrue(request.getEncodedParams().contains("gmid=mockGMID"));
    }

    @Test
    public void testUcidParamter() {
        // Arrange
        final Map<String, Object> params = new HashMap<>();
        when(_config.getUcid()).thenReturn("mockUCID");

        // Act
        final GigyaApiRequest request = _factory.create("accounts.getAccountInfo", params, RestAdapter.POST);

        // Assert
        assertNotNull(request);
        assertNotNull(request.getEncodedParams());
        assertTrue(request.getEncodedParams().contains("ucid=mockUCID"));
    }

    @Test
    public void testAnonymousRequest() {
        // Arrange
        final Map<String, Object> params = new HashMap<>();
        when(_config.getApiKey()).thenReturn("mockAPIKey");
        when(_sessionService.isValid()).thenReturn(false);

        // Act
        final GigyaApiRequest request = _factory.create("accounts.getAccountInfo", params, RestAdapter.POST);

        // Assert
        assertNotNull(request);
        assertNotNull(request.getEncodedParams());
        assertTrue(request.getEncodedParams().contains("ApiKey=mockAPIKey"));
    }

    @Test
    public void testAuthenticatedRequest() {
        // Arrange
        final Map<String, Object> params = new HashMap<>();
        when(_sessionService.isValid()).thenReturn(true);
        when(_sessionService.getSession()).thenReturn(_sessionInfo);
        when(_sessionInfo.getSessionToken()).thenReturn("mockToken");
        when(_sessionInfo.getSessionSecret()).thenReturn("bW9ja1N0cmluZw==");

        // Act
        final GigyaApiRequest request = _factory.create("accounts.getAccountInfo", params, RestAdapter.POST);

        // Assert
        assertNotNull(request);
        assertNotNull(request.getEncodedParams());
        assertTrue(request.getEncodedParams().contains("nonce="));
        assertTrue(request.getEncodedParams().contains("auth_token=mockToken"));
        assertTrue(request.getEncodedParams().contains("timestamp="));
        assertTrue(request.getEncodedParams().contains("sig="));

    }
}