# Licensed under the Apache License, csvfile.Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

"""Helpers for :mod:`grpc`."""

import collections

import grpc
import six

from bookkeeper.common import exceptions
from bookkeeper.common import general_helpers

# The list of gRPC Callable interfaces that return iterators.
_STREAM_WRAP_CLASSES = (
    grpc.UnaryStreamMultiCallable,
    grpc.StreamStreamMultiCallable,
)


def _patch_callable_name(callable_):
    """Fix-up gRPC callable attributes.
    gRPC callable lack the ``__name__`` attribute which causes
    :func:`functools.wraps` to error. This adds the attribute if needed.
    """
    if not hasattr(callable_, '__name__'):
        callable_.__name__ = callable_.__class__.__name__


def _wrap_unary_errors(callable_):
    """Map errors for Unary-Unary and Stream-Unary gRPC callables."""
    _patch_callable_name(callable_)

    @six.wraps(callable_)
    def error_remapped_callable(*args, **kwargs):
        try:
            return callable_(*args, **kwargs)
        except grpc.RpcError as exc:
            six.raise_from(exceptions.from_grpc_error(exc), exc)

    return error_remapped_callable


class _StreamingResponseIterator(grpc.Call):
    def __init__(self, wrapped):
        self._wrapped = wrapped

    def __iter__(self):
        """This iterator is also an iterable that returns itself."""
        return self

    def next(self):
        """Get the next response from the stream.
        Returns:
            protobuf.Message: A single response from the stream.
        """
        try:
            return six.next(self._wrapped)
        except grpc.RpcError as exc:
            six.raise_from(exceptions.from_grpc_error(exc), exc)

    # Alias needed for Python 2/3 support.
    __next__ = next

    # grpc.Call & grpc.RpcContext interface

    def add_callback(self, callback):
        return self._wrapped.add_callback(callback)

    def cancel(self):
        return self._wrapped.cancel()

    def code(self):
        return self._wrapped.code()

    def details(self):
        return self._wrapped.details()

    def initial_metadata(self):
        return self._wrapped.initial_metadata()

    def is_active(self):
        return self._wrapped.is_active()

    def time_remaining(self):
        return self._wrapped.time_remaining()

    def trailing_metadata(self):
        return self._wrapped.trailing_metadata()


def _wrap_stream_errors(callable_):
    """Wrap errors for Unary-Stream and Stream-Stream gRPC callables.
    The callables that return iterators require a bit more logic to re-map
    errors when iterating. This wraps both the initial invocation and the
    iterator of the return value to re-map errors.
    """
    _patch_callable_name(callable_)

    @general_helpers.wraps(callable_)
    def error_remapped_callable(*args, **kwargs):
        try:
            result = callable_(*args, **kwargs)
            return _StreamingResponseIterator(result)
        except grpc.RpcError as exc:
            six.raise_from(exceptions.from_grpc_error(exc), exc)

    return error_remapped_callable


def wrap_errors(callable_):
    """Wrap a gRPC callable and map :class:`grpc.RpcErrors` to friendly error
    classes.
    Errors raised by the gRPC callable are mapped to the appropriate
    :class:`bookkeeper.common.exceptions.BKGrpcAPICallError` subclasses.
    The original `grpc.RpcError` (which is usually also a `grpc.Call`) is
    available from the ``response`` property on the mapped exception. This
    is useful for extracting metadata from the original error.
    Args:
        callable_ (Callable): A gRPC callable.
    Returns:
        Callable: The wrapped gRPC callable.
    """
    if isinstance(callable_, _STREAM_WRAP_CLASSES):
        return _wrap_stream_errors(callable_)
    else:
        return _wrap_unary_errors(callable_)


def create_channel(target,
                   **kwargs):
    """Create a secure channel with credentials.
    Args:
        target (str): The target service address in the format 'hostname:port'.
        kwargs: Additional key-word args passed to
            :func:`grpc_gcp.secure_channel` or :func:`grpc.secure_channel`.
    Returns:
        grpc.Channel: The created channel.
    """
    return grpc.secure_channel(target, None, **kwargs)


_MethodCall = collections.namedtuple(
    '_MethodCall', ('request', 'timeout', 'metadata', 'credentials'))

_ChannelRequest = collections.namedtuple(
    '_ChannelRequest', ('method', 'request'))


class _CallableStub(object):
    """Stub for the grpc.*MultiCallable interfaces."""

    def __init__(self, method, channel):
        self._method = method
        self._channel = channel
        self.response = None
        """Union[protobuf.Message, Callable[protobuf.Message], exception]:
        The response to give when invoking this callable. If this is a
        callable, it will be invoked with the request protobuf. If it's an
        exception, the exception will be raised when this is invoked.
        """
        self.responses = None
        """Iterator[
            Union[protobuf.Message, Callable[protobuf.Message], exception]]:
        An iterator of responses. If specified, self.response will be populated
        on each invocation by calling ``next(self.responses)``."""
        self.requests = []
        """List[protobuf.Message]: All requests sent to this callable."""
        self.calls = []
        """List[Tuple]: All invocations of this callable. Each tuple is the
        request, timeout, metadata, and credentials."""

    def __call__(self, request, timeout=None, metadata=None, credentials=None):
        self._channel.requests.append(
            _ChannelRequest(self._method, request))
        self.calls.append(
            _MethodCall(request, timeout, metadata, credentials))
        self.requests.append(request)

        response = self.response
        if self.responses is not None:
            if response is None:
                response = next(self.responses)
            else:
                raise ValueError(
                    '{method}.response and {method}.responses are mutually '
                    'exclusive.'.format(method=self._method))

        if callable(response):
            return response(request)

        if isinstance(response, Exception):
            raise response

        if response is not None:
            return response

        raise ValueError(
            'Method stub for "{}" has no response.'.format(self._method))


def _simplify_method_name(method):
    """Simplifies a gRPC method name.
    When gRPC invokes the channel to create a callable, it gives a full
    method name like "/org.apache.bookkeeper.Table/Put". This
    returns just the name of the method, in this case "Put".
    Args:
        method (str): The name of the method.
    Returns:
        str: The simplified name of the method.
    """
    return method.rsplit('/', 1).pop()


class ChannelStub(grpc.Channel):
    """A testing stub for the grpc.Channel interface.
    This can be used to test any client that eventually uses a gRPC channel
    to communicate. By passing in a channel stub, you can configure which
    responses are returned and track which requests are made.
    For example:
    .. code-block:: python
        channel_stub = grpc_helpers.ChannelStub()
        client = FooClient(channel=channel_stub)
        channel_stub.GetFoo.response = foo_pb2.Foo(name='bar')
        foo = client.get_foo(labels=['baz'])
        assert foo.name == 'bar'
        assert channel_stub.GetFoo.requests[0].labels = ['baz']
    Each method on the stub can be accessed and configured on the channel.
    Here's some examples of various configurations:
    .. code-block:: python
        # Return a basic response:
        channel_stub.GetFoo.response = foo_pb2.Foo(name='bar')
        assert client.get_foo().name == 'bar'
        # Raise an exception:
        channel_stub.GetFoo.response = NotFound('...')
        with pytest.raises(NotFound):
            client.get_foo()
        # Use a sequence of responses:
        channel_stub.GetFoo.responses = iter([
            foo_pb2.Foo(name='bar'),
            foo_pb2.Foo(name='baz'),
        ])
        assert client.get_foo().name == 'bar'
        assert client.get_foo().name == 'baz'
        # Use a callable
        def on_get_foo(request):
            return foo_pb2.Foo(name='bar' + request.id)
        channel_stub.GetFoo.response = on_get_foo
        assert client.get_foo(id='123').name == 'bar123'
    """

    def __init__(self, responses=[]):
        self.requests = []
        """Sequence[Tuple[str, protobuf.Message]]: A list of all requests made
        on this channel in order. The tuple is of method name, request
        message."""
        self._method_stubs = {}

    def _stub_for_method(self, method):
        method = _simplify_method_name(method)
        self._method_stubs[method] = _CallableStub(method, self)
        return self._method_stubs[method]

    def __getattr__(self, key):
        try:
            return self._method_stubs[key]
        except KeyError:
            raise AttributeError

    def unary_unary(
            self, method,
            request_serializer=None, response_deserializer=None):
        """grpc.Channel.unary_unary implementation."""
        return self._stub_for_method(method)

    def unary_stream(
            self, method,
            request_serializer=None, response_deserializer=None):
        """grpc.Channel.unary_stream implementation."""
        return self._stub_for_method(method)

    def stream_unary(
            self, method,
            request_serializer=None, response_deserializer=None):
        """grpc.Channel.stream_unary implementation."""
        return self._stub_for_method(method)

    def stream_stream(
            self, method,
            request_serializer=None, response_deserializer=None):
        """grpc.Channel.stream_stream implementation."""
        return self._stub_for_method(method)

    def subscribe(self, callback, try_to_connect=False):
        """grpc.Channel.subscribe implementation."""
        pass

    def unsubscribe(self, callback):
        """grpc.Channel.unsubscribe implementation."""
        pass

    def close(self):
        """grpc.Channel.close implementation."""
        pass
