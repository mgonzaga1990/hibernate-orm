package org.hibernate.id.enhanced;

import junit.framework.Assert;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import org.hibernate.id.IdentifierGeneratorHelper;
import org.hibernate.id.IntegralDataTypeHolder;

/**
 * {@inheritDoc}
 *
 * @author Steve Ebersole
 */
public class OptimizerUnitTest extends TestCase {
	public OptimizerUnitTest(String string) {
		super( string );
	}

	public static Test suite() {
		return new TestSuite( OptimizerUnitTest.class );
	}

	public void testBasicNoOptimizerUsage() {
		// test historic sequence behavior, where the initial values start at 1...
		SourceMock sequence = new SourceMock( 1 );
		Optimizer optimizer = OptimizerFactory.buildOptimizer( OptimizerFactory.NONE, Long.class, 1 );
		for ( int i = 1; i < 11; i++ ) {
			final Long next = ( Long ) optimizer.generate( sequence );
			Assert.assertEquals( i, next.intValue() );
		}
		Assert.assertEquals( 10, sequence.getTimesCalled() );
		Assert.assertEquals( 10, sequence.getCurrentValue() );

		// test historic table behavior, where the initial values started at 0 (we now force 1 to be the first used id value)
		sequence = new SourceMock( 0 );
		optimizer = OptimizerFactory.buildOptimizer( OptimizerFactory.NONE, Long.class, 1 );
		for ( int i = 1; i < 11; i++ ) {
			final Long next = ( Long ) optimizer.generate( sequence );
			Assert.assertEquals( i, next.intValue() );
		}
		Assert.assertEquals( 11, sequence.getTimesCalled() ); // an extra time to get to 1 initially
		Assert.assertEquals( 10, sequence.getCurrentValue() );
	}

	public void testBasicHiLoOptimizerUsage() {
		int increment = 10;
		Long next;

		// test historic sequence behavior, where the initial values start at 1...
		SourceMock sequence = new SourceMock( 1 );
		Optimizer optimizer = OptimizerFactory.buildOptimizer( OptimizerFactory.HILO, Long.class, increment );
		for ( int i = 1; i <= increment; i++ ) {
			next = ( Long ) optimizer.generate( sequence );
			Assert.assertEquals( i, next.intValue() );
		}
		Assert.assertEquals( 1, sequence.getTimesCalled() ); // once to initialze state
		Assert.assertEquals( 1, sequence.getCurrentValue() );
		// force a "clock over"
		next = ( Long ) optimizer.generate( sequence );
		Assert.assertEquals( 11, next.intValue() );
		Assert.assertEquals( 2, sequence.getTimesCalled() );
		Assert.assertEquals( 2, sequence.getCurrentValue() );

		// test historic table behavior, where the initial values started at 0 (we now force 1 to be the first used id value)
		sequence = new SourceMock( 0 );
		optimizer = OptimizerFactory.buildOptimizer( OptimizerFactory.HILO, Long.class, increment );
		for ( int i = 1; i <= increment; i++ ) {
			next = ( Long ) optimizer.generate( sequence );
			Assert.assertEquals( i, next.intValue() );
		}
		Assert.assertEquals( 2, sequence.getTimesCalled() ); // here have have an extra call to get to 1 initially
		Assert.assertEquals( 1, sequence.getCurrentValue() );
		// force a "clock over"
		next = ( Long ) optimizer.generate( sequence );
		Assert.assertEquals( 11, next.intValue() );
		Assert.assertEquals( 3, sequence.getTimesCalled() );
		Assert.assertEquals( 2, sequence.getCurrentValue() );
	}

	public void testBasicPooledOptimizerUsage() {
		Long next;
		// test historic sequence behavior, where the initial values start at 1...
		SourceMock sequence = new SourceMock( 1, 10 );
		Optimizer optimizer = OptimizerFactory.buildOptimizer( OptimizerFactory.POOL, Long.class, 10 );
		for ( int i = 1; i < 11; i++ ) {
			next = ( Long ) optimizer.generate( sequence );
			Assert.assertEquals( i, next.intValue() );
		}
		Assert.assertEquals( 2, sequence.getTimesCalled() ); // twice to initialize state
		Assert.assertEquals( 11, sequence.getCurrentValue() );
		// force a "clock over"
		next = ( Long ) optimizer.generate( sequence );
		Assert.assertEquals( 11, next.intValue() );
		Assert.assertEquals( 3, sequence.getTimesCalled() );
		Assert.assertEquals( 21, sequence.getCurrentValue() );
	}

	public void testSubsequentPooledOptimizerUsage() {
		// test the pooled optimizer in situation where the sequence is already beyond its initial value on init.
		//		cheat by telling the sequence to start with 1000
		final SourceMock sequence = new SourceMock( 1000, 3, 5 );
		//		but tell the optimizer the start-with is 1
		final Optimizer optimizer = OptimizerFactory.buildOptimizer( OptimizerFactory.POOL, Long.class, 3, 1 );

		assertEquals( 5, sequence.getTimesCalled() );
		assertEquals( 1000, sequence.getCurrentValue() );

		Long next = (Long) optimizer.generate( sequence );
		assertEquals( 1000, next.intValue() );
		assertEquals( (5+1), sequence.getTimesCalled() );
		assertEquals( (1000+3), sequence.getCurrentValue() );

		next = (Long) optimizer.generate( sequence );
		assertEquals( 1001, next.intValue() );
		assertEquals( (5+1), sequence.getTimesCalled() );
		assertEquals( (1000+3), sequence.getCurrentValue() );

		next = (Long) optimizer.generate( sequence );
		assertEquals( 1002, next.intValue() );
		assertEquals( (5+1), sequence.getTimesCalled() );
		assertEquals( (1000+3), sequence.getCurrentValue() );

		// force a "clock over"
		next = (Long) optimizer.generate( sequence );
		assertEquals( 1003, next.intValue() );
		assertEquals( (5+2), sequence.getTimesCalled() );
		assertEquals( (1000+6), sequence.getCurrentValue() );
	}

	private static class SourceMock implements AccessCallback {
		private IdentifierGeneratorHelper.BasicHolder value = new IdentifierGeneratorHelper.BasicHolder( Long.class );
		private long initialValue;
		private int increment;
		private int timesCalled = 0;

		public SourceMock(long initialValue) {
			this( initialValue, 1 );
		}

		public SourceMock(long initialValue, int increment) {
			this( initialValue, increment, 0 );
		}

		public SourceMock(long initialValue, int increment, int timesCalled) {
			this.increment = increment;
			this.timesCalled = timesCalled;
			if ( timesCalled != 0 ) {
				this.value.initialize( initialValue );
				this.initialValue = 1;
			}
			else {
				this.initialValue = initialValue;
			}
		}

		public IntegralDataTypeHolder getNextValue() {
			try {
				if ( timesCalled == 0 ) {
					initValue();
					return value.copy();
				}
				else {
					return value.add( increment ).copy();
				}
			}
			finally {
				timesCalled++;
			}
		}

		private void initValue() {
			this.value.initialize( initialValue );
		}

		public int getTimesCalled() {
			return timesCalled;
		}

		public long getCurrentValue() {
			return value == null ? -1 : value.getActualLongValue();
		}
	}

}
