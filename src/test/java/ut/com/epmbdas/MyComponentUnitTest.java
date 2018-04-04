package ut.com.epmbdas;

import org.junit.Test;
import com.epmbdas.api.MyPluginComponent;
import com.epmbdas.impl.MyPluginComponentImpl;

import static org.junit.Assert.assertEquals;

public class MyComponentUnitTest
{
    @Test
    public void testMyName()
    {
        MyPluginComponent component = new MyPluginComponentImpl(null);
        assertEquals("names do not match!", "myComponent",component.getName());
    }
}